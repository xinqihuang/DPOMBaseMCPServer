## Context

存量工具回填。原始 spec：`docs/specs/tools/query_lts_logs.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T17-query-lts-logs.md`（状态 Done）。本工具依赖 T16 已交付的 `agentic-adapter-lts`（`LtsLogAdapter` + `LtsListLogsRequest` / `LtsListLogsResponse`）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射表、错误码 → retryable、限流 / 重试 / 超时 / 可观测、以及不一致的时间参数格式与 AI 易错点。

## Goals / Non-Goals

**Goals:**
- 在给定 `log_group_id` + `log_stream_id` 下无损暴露 LTS `listLogs` 的检索能力（时间窗 / 关键字 / 标签 / SQL）。
- 支持 `line_num` + `cursor_time` 游标分页、`scroll_id` 分页、`is_analysis_query` SQL 分析模式。
- 与 `query_lts_log_context`（T18）形成可衔接的日志下钻链。

**Non-Goals:**
- 不提供 `list_log_groups` / `list_log_streams` 等发现性工具（id 须从 CMDB / 上层 prompt context 获取）。
- 不返回 trace / metric / 拓扑。
- 不做客户端结果分桶 / 关键字高亮拼装（高亮交给上游 `highlight=true`）。
- 不做 SQL 语法校验（交给上游）；不做跨 region / 跨 projectId。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.lts.v2.LtsClient`
- **SDK 方法**：`listLogs(ListLogsRequest)`
- **SDK 版本**：3.1.177（项目共用 `${huaweicloud-sdk.version}`；字段缺失先怀疑版本）
- **HTTP**：`POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query`
- adapter 层（T16）已完成 SDK ↔ DTO 映射，service / tool 全程使用 DTO，不接触 SDK 类型。

**入参字段映射（MCP 输入 → SDK Request 字段）：**

| MCP 输入 | SDK Request 字段 | 备注 |
|---|---|---|
| `log_group_id` | path `log_group_id` | |
| `log_stream_id` | path `log_stream_id` | |
| `start_time_millis` | body `start_time` | **`String.valueOf(long)`** |
| `end_time_millis` | body `end_time` | 同上，字符串化 |
| `labels` | body `labels` | |
| `keywords` | body `keywords` | |
| `query` | body `query` | |
| `is_analysis_query` | body `is_analysis_query` | |
| `is_count` | body `is_count` | |
| `limit` | body `limit` | |
| `is_desc` | body `is_desc` | |
| `highlight` | body `highlight` | |
| `is_iterative` | body `is_iterative` | |
| `search_type` | body `search_type` | `QueryLtsLogParams.SearchTypeEnum.fromValue` |
| `line_num` | body `line_num` | |
| `cursor_time` | body `__time__` | DTO 用 `cursorTime`，adapter 映射到 `__time__` |
| `scroll_id` | body `scroll_id` | |

### 不一致的时间参数格式（重点）

LTS 与其他工具的时间约定不一致，须特别留意：
- MCP 入参 `start_time_millis` / `end_time_millis` 是 **UTC 毫秒（long）**，与 APM `list_apm_alarm_data` 的上游 String 时间、CES v2 alarm history 的 `OffsetDateTime` 均不同。
- adapter 将其 **字符串化**（`String.valueOf(long)`）后写入 SDK body `start_time` / `end_time`。
- 游标 `cursor_time` 是上一页尾行的 `__time__`（**纳秒时间戳字符串**），与 `line_num`（同为纳秒时间戳格式字符串）配对；二者均为 String，不要与毫秒时间窗混淆。
- 对照表：LTS = `startMillis` / `endMillis`（long，毫秒）；上游 APM = String（未固定格式）；CES = ISO8601 `OffsetDateTime`。本工具不引入 `durationMinutes` 概念。

### 校验决策（service 层执行）

1. `log_group_id` / `log_stream_id` 必填且非空白 → 否则 `INVALID_PARAM`。
2. `start_time_millis` 与 `end_time_millis` 同时给时，要求 `start < end`（`start >= end` 拒绝）→ 否则 `INVALID_PARAM`。
3. `limit` 非 null 时必须在 `[1, 5000]` → 否则 `INVALID_PARAM`。**注意上限 5000，与 CES / AOM 的 1000 不同**，LTS 上游允许更大单页。
4. `search_type` 非 null 时必须为 `forwards` / `backwards`。按 ADR-004 这是 lenient catalog 而非严格枚举：service 用 `Set<String>` 校验拒绝未知值，**不**在 tool 层做 enum 解析。
5. `line_num` 与 `cursor_time` 必须**同时给或同时不给**（成对游标），单边给抛 `INVALID_PARAM`——单边游标会让 SDK 行为难预测。

### 错误码 → retryable 映射

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败 | `INVALID_PARAM` | false |
| HTTP 429 / SDK throttling | `UPSTREAM_THROTTLED` | true |
| HTTP 401/403 | `UPSTREAM_AUTH_FAILED` | false |
| HTTP 5xx | `UPSTREAM_ERROR` | true |
| 调用超时 | `TIMEOUT` | true |
| 序列化 / 未分类异常 | `INTERNAL` | false |

失败响应携带 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。tool 层只 catch `SmartomException`，转 `ErrorResponse.of(errorCode, message, upstreamTraceId)`。

### 非功能（限流 / 重试 / 超时 / 可观测）

- **限流**：`lts-readonly` RateLimiter（10 QPS），与 LTS 其他读 API 共享。
- **重试**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避。
- **超时**：SDK 传输层 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="query_lts_logs", result="success|error", error_code="..."}`；INFO 日志含 `logGroupId` / `logStreamId` / `startTimeMillis` / `endTimeMillis` / `keywords` / `query` / `limit` / 耗时 / `upstream_trace_id`。

## Risks / Trade-offs

- **17 个 `@ToolParam`**：参数规模大，tool 构造 `LtsListLogsRequest` 时按位置传 17 字段，错一个全错；Spring AI 1.0.4 可承载此量级。
- **AI 易错点**：
  1. tool 层参数顺序须与 `LtsListLogsRequest`（17 字段 record）严格一致。
  2. `query` 是 SQL / 表达式（配合 `is_analysis_query=true` 启用 SQL 分析），LLM 易误当 keyword 透传，description 须明确。
  3. `Map<String,String> labels` 输入 JSON 必须是 `{"key":"value"}` 而非数组。
  4. `__time__` 命名转换由 adapter 负责，tool / service 全程用 `cursorTime`，不要二次转换。
  5. `is_analysis_query=true` 时业务结果走 `analysis_logs`，`logs` 可能为空；`is_query_complete=false` 表示需继续分页。
- **遗留**：MCP `annotations`（`readOnlyHint` 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
