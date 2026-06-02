# Spec: correlate_incident

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在事故分析时，**一次调用**同时从 CES（基础设施告警）、AOM（应用 / 节点日志）、APM（请求 trace + 拓扑）三个组件拉取证据，避免 Agent 自己串行编排多个 tool。

典型场景:
- 收到告警后 Agent 在同一时间窗内并行拿到 CES 告警历史 + AOM 错误日志 + APM 慢调用 trace
- 事后复盘要构建跨组件证据链：哪些主机出告警 / 应用打了什么错日志 / 哪条请求触发链路异常
- Agent 拿到 `trace_id` 后想同时看 trace 详情和上下游拓扑

定位:
- 这是一个**编排型 tool**，不直接调华为云 SDK，而是组合 `CesAlarmService` / `AomLogService` / `ApmTraceService` 已有能力
- 它**不替代**单一组件的查询 tool：当 Agent 只想看一个维度时仍应直接调对应 tool（响应更小、错误隔离）
- 采用**分支独立 + 并发扇出**模式：CES / AOM / APM 各自一个分支，任一分支失败 / 跳过不会影响其他分支，最终在 `CorrelateBranchResult` 三态（`success` / `failure` / `skipped`）中分别返回

## 2. 范围边界

**做**:
- 接收一个时间窗 `(startTimeMillis, endTimeMillis)` 与一组可选过滤条件
- 并发扇出 4 个分支：`ces_alarms` / `aom_logs` / `apm_traces` / `apm_topology`
- 每个分支在输入缺失时记 `skipped=true`（不算失败）
- 单分支 `SmartomException` 被捕获后转 `CorrelateError`，**不阻塞其他分支**
- 用 JDK 21 虚拟线程 (`Executors.newVirtualThreadPerTaskExecutor()`) 承载分支
- 全部分支完成后统一返回 `CorrelateIncidentResponse`

**不做**:
- 不做跨组件的语义关联 / 因果推断（那是 Agent 侧 LLM 的工作）
- 不做单分支的限流 / 重试（由下游 service 透传，本层不重复包裹）
- 不做分页（每分支固定取首页：alarms 100 / logs 100 / traces 50 / topology 单次）
- 不做超时熔断（受下游 SDK 超时约束；本层等所有分支完成）
- 不接受跨 region / 跨 projectId 调用

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `correlate_incident`
- description（Agent 看到的）:

  > Cross-component incident correlation: in a single call, concurrently query
  > CES alarms, AOM logs, and APM traces (+ topology) within a time window.
  > Use this when you need a fast cross-cut view of what was happening at the
  > time of an incident — across infrastructure (CES), application logs (AOM),
  > and request traces (APM). Each branch is independent: a failing branch does
  > not affect the others, and any branch whose required input is missing is
  > reported as 'skipped' rather than failing the whole call.

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `startTimeMillis` | long | 是 | — | 时间窗起点（UTC 毫秒） |
| `endTimeMillis` | long | 是 | — | 时间窗终点（UTC 毫秒），必须 `>` 起点 |
| `cesNamespace` | string | 否 | null | CES namespace 过滤，如 `SYS.ECS`（影响 ces_alarms 分支） |
| `logCategory` | string | 否 | null | AOM 日志类型 `app_log` / `node_log` / `custom_log`；**为空时 aom_logs 分支 skipped** |
| `logKeyword` | string | 否 | null | AOM 日志关键字 |
| `apmTraceId` | string | 否 | null | APM trace id；为空时 apm_topology 分支 skipped |
| `apmSource` | string | 否 | null | APM 入口资源；与 `apmTraceId` 互补，二者均空时 apm_traces 分支 skipped |

**输入校验规则**（Service 层）:
- `request == null` → `INVALID_PARAM`
- `startTimeMillis` / `endTimeMillis` 任一为 null → `INVALID_PARAM`
- `endTimeMillis <= startTimeMillis` → `INVALID_PARAM`

### 3.3 输出契约（成功）

```json
{
  "cesAlarms":   {"skipped": false, "data": { /* CES alarm history */ }, "error": null},
  "aomLogs":     {"skipped": false, "data": { /* AOM ListLogItems result */ }, "error": null},
  "apmTraces":   {"skipped": true,  "data": null, "error": null},
  "apmTopology": {"skipped": false, "data": null,
                   "error": {"errorCode": "UPSTREAM_ERROR", "retryable": true,
                              "message": "...", "upstreamTraceId": "..."}}
}
```

字段说明:
- 每个分支固定返回三个字段 `skipped` / `data` / `error`，**三者互斥**：
  - `skipped=true` 表示输入缺失，未访问上游
  - `data != null` 表示分支成功，载荷是该 service 的响应 DTO
  - `error != null` 表示分支失败，结构见 §3.4
- 顺序固定为 `cesAlarms` → `aomLogs` → `apmTraces` → `apmTopology`
- 即使部分分支失败 / 跳过，整体 HTTP 状态仍是成功，由 Agent 自行判断每个分支状态

### 3.4 输出契约（失败）

整体失败（输入校验未通过）走标准 `ErrorResponse`：

```json
{
  "error_code": "INVALID_PARAM",
  "error_message": "end_time_millis must be > start_time_millis",
  "upstream_trace_id": null,
  "retryable": false
}
```

单分支失败封装在 `CorrelateError`：

```json
{
  "errorCode": "UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "retryable": true | false,
  "message": "...",
  "upstreamTraceId": "华为云返回的 X-Request-Id, 可能为 null"
}
```

错误码映射（针对单分支）:

| 上游情况 | error_code | retryable |
|---|---|---|
| 时间窗校验失败 | INVALID_PARAM（整体失败） | false |
| 下游 service 抛 UpstreamException(429) | UPSTREAM_THROTTLED | true |
| 下游抛 UpstreamException(401/403) | UPSTREAM_AUTH_FAILED | false |
| 下游抛 UpstreamException(5xx) | UPSTREAM_ERROR | true |
| 分支线程被中断 / ExecutionException | INTERNAL | false |

## 4. 与下游 service 的映射

correlate_incident **不直连华为云 SDK**，而是组合 monitoring 层已有的 service：

| 分支 | 下游 service | 入参构造（关键字段） |
|---|---|---|
| `ces_alarms` | `CesAlarmService.listAlarms(...)` | `CesListAlarmsRequest`：namespace + 时间窗（字符串）+ offset=0 + limit=100 |
| `aom_logs` | `AomLogService.queryLogs(...)` | `AomQueryLogsRequest`：category + 时间窗 + keyword + pageSize=100 + isDesc=true |
| `apm_traces` | `ApmTraceService.queryTraces(...)` | `ApmQueryTracesRequest`：traceId + source + page=1 + pageSize=50 |
| `apm_topology` | `ApmTraceService.getTopology(...)` | `ApmGetTopologyRequest`：traceId |

**AI 容易写错的点**:
1. **CES 告警时间字段是字符串**：`CesListAlarmsRequest` 接收毫秒字符串，本层用 `String.valueOf(...)` 转换
2. **AOM 日志 / APM trace 分支的跳过判断**：`logCategory` 空 → aom_logs skipped；`apmTraceId && apmSource` 均空 → apm_traces skipped；`apmTraceId` 空 → apm_topology skipped。**不要把跳过逻辑放在 service 层**，它属于编排策略
3. **`CompletableFuture.allOf().join()` 不抛业务异常**：单分支异常已在 `runBranch` 内被 `SmartomException` catch；只有 `InterruptedException` / `ExecutionException` 会在 `waitFor` 中处理
4. **不要 catch `Exception`**：仅捕获 `SmartomException`（业务异常）；其他异常透传到 Spring AI 框架（违反 CLAUDE.md §3.4）
5. **`CorrelateBranchResult.data` 类型是 `Object`**：序列化时 Jackson 直接展开下游 DTO 的字段名（不做转换）

## 5. 非功能要求

- **并发模型**: 每次调用创建一个 `Executors.newVirtualThreadPerTaskExecutor()`（try-with-resources 关闭），4 个分支并发执行；与 CLAUDE.md §4.4 一致
- **限流**: 不在本层重复施加；下游 service 透传 `ces-readonly` / `aom-readonly` / `apm-readonly` 三个 RateLimiter（一次 correlate 调用扣 4 个配额，跨三个限流域）
- **重试**: 同上，由下游 service 透传，本层不重复
- **超时**: 不在本层强加；受下游 SDK 默认 10s 约束 × 4 分支并发 ⇒ 整体期望 P99 < 12s
- **可观测**:
  - Micrometer 指标: `mcp_tool_invocation{tool="correlate_incident", result="success|error", error_code="..."}`（仅整体；分支级指标由下游 service 提供）
  - 日志 INFO: 入参摘要（时间窗 + 各过滤项是否提供）+ 单分支失败 warn（含 `errorCode` + `upstreamTraceId`）

## 6. 测试策略（Definition of Done）

### 单元测试

本期未交付（编排逻辑的 mock 矩阵：4 分支 × 3 状态 × 2 异常类型）。后续建议在 `agentic-monitoring` 模块补 `CorrelateIncidentServiceTest`：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | 全字段合法 → 4 分支均成功 | 4 个 `success` 状态，data 非 null |
| UT-02 | `logCategory=null` | aom_logs 为 `skipped` |
| UT-03 | `apmTraceId/apmSource` 均空 | apm_traces 为 `skipped`，apm_topology 为 `skipped` |
| UT-04 | `apmTraceId` 给定但 `apmSource` 空 | apm_traces success，apm_topology success |
| UT-05 | CES service 抛 UpstreamException(5xx) | ces_alarms 为 `failure`，其他分支不受影响 |
| UT-06 | `startTimeMillis == endTimeMillis` | 整体 INVALID_PARAM，不创建 executor |
| UT-07 | request 为 null | INVALID_PARAM |
| UT-08 | 单分支 throw RuntimeException（非 SmartomException） | 透出到 ExecutionException → `INTERNAL` failure |

### 类型契约测试

本期未交付，无直接 SDK 类型可契约（编排层）。

### 部署后冒烟

本期未交付。后续建议 `scripts/smoke/smoke-correlate_incident.sh`：

1. 给定最近 1 小时 + `logCategory=app_log` + 有效 `traceId` → 断言 4 分支均 success
2. 不给 `logCategory` + 不给 `traceId` → 断言 ces_alarms success，其余 3 个 skipped
3. `endTimeMillis < startTimeMillis` → INVALID_PARAM

## 7. 验收标准（DoD）

- [x] Tool 注册类 `CorrelateIncidentTool` 实现 + `@Tool` description 经过 MCP Inspector 验证
- [x] Service 层 `CorrelateIncidentService` 时间窗校验完整（4 条 InvalidParamException）
- [x] 分支独立性：单分支 SmartomException → CorrelateError，不影响其他分支
- [x] 虚拟线程扇出，符合 CLAUDE.md §4.4
- [x] 日志含 errorCode / upstreamTraceId（分支级 warn）
- [x] 代码已合入 master（提交 `4c346d6`）
- [x] Checkstyle 0 violations
- [ ] Service 层 UT-01~08（后续任务补）
- [ ] Micrometer 指标 + 贵阳冒烟脚本 + README 示例（后续任务补）
