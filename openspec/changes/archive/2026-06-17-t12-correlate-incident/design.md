## Context

存量编排型工具回填。原始 spec：`docs/specs/tools/correlate_incident.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T12-correlate-incident.md`（状态 Done，提交 `4c346d6`）。本文承载 OpenSpec 主 spec 放不下的重契约：并发模型、下游 service 映射、各分支不一致的时间参数格式、错误码→retryable 映射、非功能要求、以及 AI 易错点。

`correlate_incident` 是 monitoring 链上唯一的**编排型** tool：它不直连华为云 SDK，而是组合 `CesAlarmService` / `AomLogService` / `ApmTraceService` 三个底座 service（依赖 T08-T10），并依赖 T06 的 AOM adapter。它**不替代**单组件查询 tool：当 Agent 只关心一个维度时仍应直接调对应 tool（响应更小、错误隔离）。

## Goals / Non-Goals

**Goals:**
- 在一次 MCP 调用内并发拉取 CES / AOM / APM 四分支证据，降低 Agent 多轮编排成本。
- 分支独立：任一分支失败 / 输入缺失，均不影响其他分支，以三态 `success` / `failure` / `skipped` 分别返回。
- 用 JDK 21 虚拟线程扇出，符合 CLAUDE.md §4.4 并发模型。

**Non-Goals:**
- 不做跨组件的语义关联 / 因果推断（那是 Agent 侧 LLM 的工作）。
- 不在本层做限流 / 重试 / 超时熔断（由下游 service 透传，本层不重复包裹）。
- 不做分页（每分支固定取首页）；不接受跨 region / 跨 projectId 调用。

## Decisions

### 并发模型（CLAUDE.md §4.4）

每次调用 try-with-resources 创建独立的 `Executors.newVirtualThreadPerTaskExecutor()`，对四个分支各 `CompletableFuture.supplyAsync(..., executor)`，随后 `CompletableFuture.allOf(...).join()` 聚合。JDK 21 虚拟线程 executor 实现了 `AutoCloseable`，离开 try-with-resources 作用域会 await 所有任务结束，**不需要手动 `shutdown()`**。

### 下游 service 映射与时间参数格式（关键：四分支格式不一致）

| 分支 | 下游 service / 方法 | 入参构造（关键字段） | 时间参数格式 |
|---|---|---|---|
| `ces_alarms` | `CesAlarmService.listAlarms(CesListAlarmsRequest)` | namespace + 时间窗 + offset=0 + limit=100 | **CES 时间字段是字符串**，本层用 `String.valueOf(millis)` 转换 |
| `aom_logs` | `AomLogService.queryLogs(AomQueryLogsRequest)` | category + 时间窗 + keyword + pageSize=100 + isDesc=true | 时间窗为 UTC 毫秒（`startMillis` / `endMillis`，long） |
| `apm_traces` | `ApmTraceService.queryTraces(ApmQueryTracesRequest)` | traceId + source + page=1 + pageSize=50 | 不直接传时间窗（按 traceId / source 检索） |
| `apm_topology` | `ApmTraceService.getTopology(ApmGetTopologyRequest)` | traceId | 不传时间窗 |

> 入口 tool 入参 `startTimeMillis` / `endTimeMillis` 统一为 UTC 毫秒 `long`；扇出到 CES 时转字符串、扇出到 AOM 时保持毫秒，APM 两分支不消费时间窗——**这是 AI 最易写错的不一致点**。

### 跳过策略（属于编排层，不下沉到下游 service）

- `logCategory` 为空 → `aom_logs` 分支 `skipped`。
- `apmTraceId` 与 `apmSource` 均为空 → `apm_traces` 分支 `skipped`。
- `apmTraceId` 为空 → `apm_topology` 分支 `skipped`。
- `cesAlarms` 分支**不跳过**：`cesNamespace` 为空表示不加 namespace 过滤，而非跳过。

跳过判断写在 `runAomLogs` / `runApmTraces` / `runApmTopology` 内部 → `CorrelateBranchResult.ofSkipped()`，**不放在 service 编排层**。

### 分支独立异常处理与错误码→retryable 映射

`runBranch(label, supplier)` 仅单点 catch `SmartomException`（业务异常），转 `CorrelateError(errorCode, errorCode.isRetryable(), message, upstreamTraceId)`，并打 WARN（含 `errorCode` + `upstreamTraceId`）。**不要 catch `Exception`**（违反 CLAUDE.md §3.4）：RuntimeException 经 `CompletableFuture` 包装成 `ExecutionException`，由 `waitFor` 兜底转 `INTERNAL`。

| 上游情况 | error_code | retryable |
|---|---|---|
| 时间窗校验失败（整体失败，走标准 `ErrorResponse`） | `INVALID_PARAM` | false |
| 下游抛 `UpstreamException(429)` | `UPSTREAM_THROTTLED` | true |
| 下游抛 `UpstreamException(401/403)` | `UPSTREAM_AUTH_FAILED` | false |
| 下游抛 `UpstreamException(5xx)` | `UPSTREAM_ERROR` | true |
| 分支线程被中断 / `ExecutionException` | `INTERNAL` | false |

### 三态输出契约

每分支固定返回 `skipped` / `data` / `error` 三个字段，三者互斥；分支顺序固定 `cesAlarms` → `aomLogs` → `apmTraces` → `apmTopology`。即使部分分支失败 / 跳过，整体仍为成功响应，由 Agent 自行判断每分支状态。`CorrelateError` 结构：`errorCode` / `retryable` / `message` / `upstreamTraceId`（华为云 `X-Request-Id`，可空）。

## Risks / Trade-offs

- **非功能**：限流不在本层重复施加，由下游透传 `ces-readonly` / `aom-readonly` / `apm-readonly` 三个 RateLimiter（一次 correlate 调用扣 4 个配额，跨三个限流域）；重试同样由下游透传；超时不在本层强加，受下游 SDK 默认 10s 约束 × 4 分支并发 ⇒ 整体期望 P99 < 12s；可观测：Micrometer `mcp_tool_invocation{tool="correlate_incident", result, error_code}`（仅整体级，分支级指标由下游 service 提供），INFO 日志含入参摘要（时间窗 + 各过滤项是否提供），单分支失败打 WARN。
- **`CorrelateBranchResult.data` 类型为 `Object`**：Jackson 按运行期类型展开下游各自 DTO（`CesListAlarmsResponse` / `AomQueryLogsResponse` / `ApmQueryTracesResponse` / `ApmGetTopologyResponse`），字段名由 Jackson 默认策略决定，本层不做转换。
- **AI 易错点**：(1) CES 时间字段需 `String.valueOf(millis)`；(2) 跳过判断在编排层，不下沉下游；(3) `allOf().join()` 不抛业务异常（已在 `runBranch` 内吞并转 `CorrelateError`），仅 `Interrupted` / `Execution` 异常在 `waitFor` 处理；(4) 不要 catch `Exception`；(5) Lambda 参数名 ≥ 2 字符（Checkstyle `LambdaParameterName`）；(6) `}` 与 `catch` / `else` 必须换行（CLAUDE.md §3.1）。
- **遗留**：Service 层 UT（4 分支 × 3 状态 × 2 异常矩阵）、Tool 层 UT、Micrometer 分支级指标、贵阳冒烟脚本、README 示例本期未交付（见 tasks.md 末尾）。
