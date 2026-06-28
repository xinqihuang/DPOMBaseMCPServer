## Context

存量基础设施 / 横切回填。原始任务卡：`docs/tasks/T25-tool-error-unification.md`（状态 Done，估时 0.5d，依赖 T03 common 错误模型）。本变更属 infra / 横切层，无对外 capability spec，因此把全部重契约（涉及的 SDK / 框架类与版本、错误码 → retryable → hint 映射、`ToolCallSupport` 行为契约、限流 / 重试 / 超时 / 可观测、各服务不一致的时间参数格式、AI 易错点）沉淀到本设计文档。

本服务是 MCP Server，消费方是大模型 Agent。错误处理的根本约束：失败必须以**结构化 JSON**返回给模型（而非抛异常给框架），让模型据 `error_code` / `retryable` / `hint` 自行决策。回填前两个缺口——非业务 `RuntimeException` 会越过工具裸抛、`retryable` 布尔不携带行动建议——本变更在 common 错误模型加 `hint`、在 mcp 层加单点兜底 `ToolCallSupport` 收口。

## Goals / Non-Goals

**Goals:**
- 任何工具调用失败都返回结构化 `ErrorResponse`，绝不把异常抛给 Spring AI MCP 框架（框架只会回传 `e.getMessage()` 一行文本）。
- 在 `ErrorCode` 单点维护面向 Agent 的 `hint` 行动建议，`ErrorResponse` 自动派生，工具层零额外文案。
- 非业务 `RuntimeException` 在 MCP 进程边界兜底为 `INTERNAL`：全栈进服务端日志，回给 Agent 的消息**只含异常类名 + message，不含堆栈**。
- 把每个工具重复的 try/catch + 日志样板收敛为 `ToolCallSupport.execute(...)` 单点。

**Non-Goals:**
- 不改任何工具入参 / 成功响应结构 / `@Tool` 描述（仅失败路径加 `hint`）。
- 不改既有错误码 → retryable 映射语义（沿用各 adapter 经 `HuaweiCloudInvocation` 的统一通道）。
- 不依赖 Spring AI `ToolExecutionExceptionProcessor`（MCP server 路径不经过它）。
- 不引入 Micrometer 埋点（另卡）。
- 不新增工具、不动分页 / 限流 / 超时配置。

## Decisions

### 架构决策（infra / 横切）

- **错误必须结构化返回，不抛框架**：Spring AI MCP Server 对 `@Tool` 方法抛出的异常只回传 `e.getMessage()` 一行文本（`isError=true`），Agent 拿不到机器可读字段。故所有失败统一转 `ErrorResponse` 作为正常返回值返回，由 MCP 框架序列化为工具结果。
- **`hint` 单点落 `agentic-common` 的 `ErrorCode`**：提示文案是错误码的固有属性，随码维护一处，工具层禁止另写——避免同一错误在不同工具给出不一致建议。`ErrorResponse` 经 `of(...)` 工厂从 `ErrorCode` 同时派生 `error_code` / `retryable` / `hint`，杜绝调用方手填导致漂移。
- **`RuntimeException` 兜底是有意例外**：`ToolCallSupport` catch `RuntimeException`（而非各类具体异常）作为 MCP 进程边界的最后兜底，属 CLAUDE.md §3.4「明确捕获类型」的有意例外，已在 Javadoc 显式说明；任何未分类异常映射为 `INTERNAL`，完整堆栈只进 ERROR 日志，回给 Agent 的消息仅含异常类名与 message。
- **包私有、与 `ToolValidations` 同级**：`ToolCallSupport` 放 `agentic-mcp` 的 `tool` 包，`final` + 私有构造，仅供 `@Component` Tool 类使用，不外泄。
- **依赖方向不变**：`mcp → monitoring → adapter → common`；本变更不新增模块、不改依赖边。`ErrorCode` / `ErrorResponse` 在 `agentic-common`，`ToolCallSupport` 在 `agentic-mcp`，单向依赖。
- **选型：不用 Spring AI `ToolExecutionExceptionProcessor`**：该处理器只在 Spring AI 自身的 ToolCallback 执行路径生效；MCP server 工具调用路径（`McpToolUtils`）不经过它，挂上去不会被触发。故采用应用层显式包装器 `ToolCallSupport` 而非框架扩展点。

### 错误模型契约（回填实现，照此）

- **`ErrorCode`**（`com.huawei.smartom.agentic.common.error.ErrorCode`，enum）：每枚举常量构造为 `(boolean retryable, String hint)`，暴露 `isRetryable()` 与 `getHint()`（`getHint()` 不返回 null / 空串）。`hint` 英文，与 `@Tool` 描述语言一致。
- **`ErrorResponse`**（同包 `record`，JSON snake_case）：组件 `errorCode`(`error_code`) / `errorMessage`(`error_message`) / `upstreamTraceId`(`upstream_trace_id`，可 null) / `retryable`(`retryable`) / `hint`(`hint`)。工厂 `static ErrorResponse of(ErrorCode code, String message, String upstreamTraceId)` 从 `code.name()` / `code.isRetryable()` / `code.getHint()` 派生。

**错误码 → retryable → hint 映射表（语义不变，hint 为本卡新增）**：

| ErrorCode | retryable | 触发 | Agent 行动建议（hint 摘要） |
|---|---|---|---|
| `INVALID_PARAM` | false | 本地入参校验失败 | 别用同参重试；重读工具描述、改参数再调 |
| `UPSTREAM_THROTTLED` | true | 上游 429 | 退避几秒重试；持续则降频 / 收窄查询 |
| `UPSTREAM_AUTH_FAILED` | false | 上游 401 / 403 | 服务端凭据 / 权限问题，改参无用；停止重试并连同 `upstream_trace_id` 上报 |
| `UPSTREAM_ERROR` | true | 上游 5xx | 短退避后重试一次；持续则带 `upstream_trace_id` 上报 |
| `TIMEOUT` | true | 调用超时 | 重试；持续超时则收窄时间窗 / 减小页大小 |
| `INTERNAL` | false | 序列化等未分类内部错误 | 非参数所致，别同样重试；换工具或带 `error_message` 上报 |

> 关键点：`retryable` 相同也可能行动不同——`INVALID_PARAM` 与 `UPSTREAM_AUTH_FAILED` 都不可重试，但前者要 Agent 改参、后者要 Agent 停手上报。这正是 `hint` 存在的理由。

### `ToolCallSupport` 行为契约

`com.huawei.smartom.agentic.mcp.tool.ToolCallSupport`（package-private，`final`，私有构造）：

```
static Object execute(String toolName, Supplier<?> action)
```

- **成功**：返回 `action.get()`；INFO 日志 `"{toolName} succeeded, durationMs={...}"`。
- **catch `SmartomException`**：返回 `ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId())`；WARN 日志含 `errorCode` + `upstreamTraceId` + 耗时（不打全栈）。
- **catch `RuntimeException`**：返回 `ErrorResponse.of(ErrorCode.INTERNAL, internalMessage(e), null)`；ERROR 日志含**全栈**。`internalMessage(e)` = `"Unexpected internal error (" + 异常类名 + ")" [+ ": " + message]`，**不含堆栈**——堆栈仅进日志，不回 Agent。
- 工具改写形态：`return ToolCallSupport.execute("<tool_name>", () -> service.xxx(req));`，`<tool_name>` 必须与该工具 `@Tool(name=...)` 一致（用于日志定位）。

> `SmartomException` 是 `RuntimeException` 子类，故必须先 catch `SmartomException` 再 catch `RuntimeException`，顺序不可颠倒。

### 限流 / 重试 / 超时 / 可观测（不变，仅记录）

- 限流 / 重试 / 超时：各工具沿用其 adapter 经 `HuaweiCloudInvocation` 的既有 `*-readonly` RateLimiter（10 QPS）、`huaweicloud-retryable` 重试域（仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避）、`defaultHttpConfig()` 传输层超时（10s）。本卡**不触碰**这些。
- 可观测：`ToolCallSupport` 成功 INFO（工具名 + 耗时）/ `SmartomException` WARN（errorCode + upstreamTraceId + 耗时）/ `RuntimeException` ERROR（全栈 + 耗时）三档日志。Micrometer 埋点不在本卡（另卡）。失败响应透传 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

### 时间参数格式（跨服务不一致，AI 高频出错点，记录以备勿误）

本卡是横切错误处理，**不解析任何时间参数**——但工具改写时会逐个动到各服务的工具方法，各华为云服务的时间参数格式**互不相同**，复制 / 迁移代码时极易引入隐性 bug，集中说明（仅作勿误备忘，本卡不改其转换）：

- **CES alarm history（v2）顶层时间**：`AlarmHistoryItemV2` 的 begin/end/firstAlarm/lastAlarm/alarmRecovery 是 **`OffsetDateTime`（ISO8601 带时区偏移，如 `2024-02-11T05:48:08+08:00`）**；但同响应内 `DataPointInfo.time` 是 **`String`**（不要统一臆测成 Long）。
- **APM `ListAlarmData`**：`AlarmDataVO` 的 `alarm_first_time` / `alarm_last_time` / `gmt_create` / `gmt_modify` 是 **`String`（上游未固定格式）**，原样透传，不本地解析。
- **LTS（`ListLogs` / `ListLogContext`）**：SDK 侧 `start_time` / `end_time` 是 **`String`，语义为 UTC 毫秒**（毫秒数的字符串形式，非 ISO8601）；adapter 对外 DTO 暴露为 **`Long`（UTC 毫秒）**，转 SDK 用 `String.valueOf(longValue)`。游标 `__time__` 在 SDK Java 字段名只叫 `time`（`getTime()` / `setTime()`），DTO 命名 `cursorTime`（String）。
- **AOM（`ListSample` / metric-data）**：时间窗常用 `startMillis` / `endMillis` / `durationMinutes` 三件套（毫秒区间 + 分钟粒度），与上面三种又不同。

> 一句话：**CES v2 顶层 = OffsetDateTime**（但其 data_points.time = String）；**APM = String 不定格式**；**LTS = "UTC 毫秒字符串"（对外 Long）**；**AOM = startMillis / endMillis / durationMinutes**。四套互不相同，照搬必错。本卡只搬错误处理，不动时间转换。

### AI 易错点（沉淀自任务卡）

1. **必须返回结构化错误，不能抛框架**：MCP server 路径下抛异常只剩 `e.getMessage()` 一行，Agent 拿不到 `error_code` / `retryable` / `hint`。
2. **catch 顺序**：`SmartomException`（业务）在前、`RuntimeException`（兜底）在后；前者是后者子类，颠倒会让业务异常走进 `INTERNAL`。
3. **`INTERNAL` 不回堆栈**：全栈只进 ERROR 日志，回 Agent 的 message 仅异常类名 + message，防止泄漏内部实现。
4. **`hint` 单点**：只在 `ErrorCode` 维护，工具层 / service 层不得另写提示文案。
5. **`ToolExecutionExceptionProcessor` 无效**：MCP server 路径不经过它，别指望靠它兜底；用 `ToolCallSupport` 显式包。
6. **`<tool_name>` 一致**：`ToolCallSupport.execute("<tool_name>", ...)` 的字面量必须等于该工具 `@Tool(name=...)`，否则日志定位错位。
7. **任务卡与真实实现冲突时停下来问，不要猜**（CLAUDE.md §5.1）。

## Risks / Trade-offs

- **`catch (RuntimeException)` 的粗粒度**：违反「明确捕获类型」的常规，但这是 MCP 进程边界的有意兜底——边界处任何漏网异常都不能裸抛给框架。缓解：仅此一处、`final` 工具类、Javadoc 显式标注例外理由，并在 `INTERNAL` 路径打全栈便于定位。
- **失败响应新增 `hint` 字段**：理论上改变了失败 JSON 形状。缓解：只增字段、不改既有字段名与语义，对解析 `error_code` / `retryable` 的 Agent 向后兼容；`hint` 缺省由枚举保证非空。
- **行为变化：`RuntimeException` 由裸抛改为 `INTERNAL`**：原先映射 NPE / 序列化错误会以一行文本 + `isError=true` 返回，现统一为结构化 `INTERNAL`（`retryable=false`）。这是预期的修复方向，但依赖「框架一行文本」做断言的旧测试需同步调整（错误路径断言）。
- **单点收敛的回归面**：把每个工具的 try/catch 改为统一包装，触及全部工具方法；删除各自 LOG 字段 / 失效 import 时易遗漏。缓解：`ToolCallSupportTest`（成功透传 / `SmartomException` → 结构化含 hint / `RuntimeException` → `INTERNAL` 不含堆栈）+ 各工具既有 UT 错误路径断言兜底，`mvn test` + `mvn checkstyle:check` 全绿。
- **遗留项**（本期未交付，列入 tasks.md）：Micrometer `mcp_tool_invocation` 埋点、Spring AI `ToolExecutionExceptionProcessor` 路径验证留痕。
