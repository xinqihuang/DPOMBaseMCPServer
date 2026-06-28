## Context

存量工具回填。原始 spec：`docs/specs/tools/query_traces.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T10-query-apm-traces.md`（状态 Done，提交 `4c346d6`）。本工具是 APM adapter 子模块（`agentic-adapter-apm`）首个 SDK 能力，承载 APM 调用链搜索的入口。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射、时间格式约定、错误码与非功能要求、AI 易错点。

## Goals / Non-Goals

**Goals:**
- 无损暴露 APM `ShowSpanSearch` 的多维 span 检索能力，供 Agent 排查延迟 / 错误问题。
- 在 `agentic-adapter-apm` 建立 APM SDK 装配与 `apm-readonly` 限流隔离基座，供后续 APM 工具复用。
- 与 `get_service_topology` 形成可衔接的调用链下钻路径（本工具出 span 列表，topology 出节点 / 边）。

**Non-Goals:**
- 不返回完整 span 树（由 `get_service_topology` 负责）。
- 不做 APM 日志 / 异常详情（走 AOM / LTS 日志工具）。
- 不做跨 region / 跨账号检索。
- 不做客户端聚合 / 排序（透传上游返回顺序）。
- 不交付 UT / Contract Test / 冒烟脚本 / Micrometer 看板（列入遗留项）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`（**不是 `ApmAsyncClient`**——后者用于响应式场景，本项目禁用 WebFlux）。
- **SDK 方法**：`showSpanSearch(ShowSpanSearchRequest)`。
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本）。
- **请求体类型**：`TraceSearchParam`，经 `ShowSpanSearchRequest.withBody(...)` 挂载。
- **HTTP 头**：`x-business-id: Long`，经 `ShowSpanSearchRequest.setXBusinessId(Long)` 注入。

**入参装配映射（DTO `ApmQueryTracesRequest` → SDK）**：

| DTO 字段 | SDK 装配位置 | 类型 | 说明 |
|---|---|---|---|
| businessId | `ShowSpanSearchRequest.setXBusinessId` | Long | **请求头** `x-business-id`，非 body |
| （region） | `TraceSearchParam.withRegion` | String | **必填**，取 `properties.getApmRegion()` |
| startTimeString / endTimeString | `TraceSearchParam.setStartTimeString` / `setEndTimeString` | String | 格式 `yyyy-MM-dd HH:mm:ss` |
| traceId | `TraceSearchParam.setTraceId` | String | 精确过滤 |
| source | `TraceSearchParam.setSource` | String | 入口 url / 方法名，模糊匹配 |
| hasError | `TraceSearchParam.setHasError` | Boolean | true 时仅返回出错 span |
| timeUsedMin | `TraceSearchParam.setTimeUsedMin` | Long | 最小耗时（毫秒），需 >= 0 |
| page | `TraceSearchParam.withPage` | Integer | 1-based，需 >= 1，默认 1 |
| pageSize | `TraceSearchParam.withPageSize` | Integer | [1, 500]，默认 50 |

**响应字段映射（SDK `ClientSpanInfo` → DTO `ApmSpan`）**：

| SDK `ClientSpanInfo` | DTO `ApmSpan` | 类型 |
|---|---|---|
| trace_id / span_id / global_trace_id | traceId / spanId / globalTraceId | String |
| source / real_source | source / realSource | String |
| class_name | className | String |
| start_time | startTime | Long（UTC 毫秒时间戳）|
| time_used | timeUsed | Long（毫秒）|
| code | code | Integer |
| has_error | hasError | Boolean |
| error_reasons | errorReasons | String |
| http_method | httpMethod | String（仅 URL 监控项有值）|
| tags | tags | Map<String,String>（**始终非 null**，`Map.of()` 兜底）|

> 顶层 `total` 由 SDK `getTotal()` 透传，可能为 `null`；span 列表取 `getSpanInfoList()`（**不是 `getSpans()`**）。

### 时间参数格式（关键 —— 与其它工具不一致）

APM `query_traces` 的时间入参 `startTimeString` / `endTimeString` 是**字符串** `yyyy-MM-dd HH:mm:ss`，而响应内 span 的 `start_time` 是 **UTC 毫秒时间戳（Long）**。这与同生态其它工具的时间格式各不相同（如 CES v2 alarm history 用 `OffsetDateTime`/ISO8601；某些指标工具用 `startMillis` / `endMillis` / `durationMinutes` 三元组毫秒入参；`list_apm_alarm_data` 上游为未固定格式字符串）。**严禁把毫秒时间戳传入 `startTimeString`，也严禁对响应 `start_time` 做字符串解析。** 入参不做本地时间解析或格式转换，原样透传上游。

### business_id 解析与 fallback

`businessId` 为 `x-business-id` 头部（Long，租户 / 应用上下文）。入参为 null 时回落到 `HuaweiCloudProperties.getApmBusinessId()` 配置默认值，**fallback 在 adapter 层做**（不在 service / tool 层）。

### Service 层校验

`page >= 1`；`pageSize ∈ [1, 500]`；`timeUsedMin >= 0`（如提供）。违反任一条返回 `INVALID_PARAM`，不发起上游调用。其余字段透传上游，不预校验取值。

### 装配与依赖

- 新增 `ApmClientConfig` 装配 `ApmClient` Spring bean；AK/SK 复用 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK`（Vault 注入），与 CES / AOM 一致。
- `McpServerConfig` 将 `ApmTraceTool` 注入 `ToolCallbackProvider`。

### AI 易错点

1. `businessId` 是 HTTP 头 `x-business-id`，用 `setXBusinessId(Long)`，**不在 body**。
2. `region` **必须**显式 `TraceSearchParam.withRegion(...)`，否则上游报参数缺失。
3. 时间入参是字符串 `yyyy-MM-dd HH:mm:ss`，**不是毫秒时间戳**（CES / APM / 各工具格式不同，别混）。
4. `ClientSpanInfo.getTags()` 可能为 null，必须 `Map.of()` 兜底。
5. 响应取 `getSpanInfoList()` + `getTotal()`，**不是 `getSpans()`**。
6. `businessId` 缺失时 fallback 走配置默认值，**在 adapter 层做**。
7. Tool 名是 `query_traces`（不是 `query_apm_traces`）；spec / capability 名跟 tool name 走（`query-traces`）。
8. APM SDK 客户端是 `ApmClient`，**不是 `ApmAsyncClient`**。
9. `apm-readonly` 与 `ces-readonly` 是两个独立 RateLimiter 实例，不复用。

## Risks / Trade-offs

- **限流隔离**：新增独立 `apm-readonly`（10 QPS），避免 CES / APM 互相挤占配额；代价是多一个限流域配置。
- **重试 / 超时**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避（200ms / 800ms / 3.2s）；SDK 传输层超时 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="query_traces"}`；adapter `apm.showSpanSearch start` INFO 含 businessId / traceId / source / page / pageSize / 耗时 / upstream trace id。
- **`total` 可空**：上游 `getTotal()` 可能返回 null，Agent 侧需容忍。
- **annotations 语义意图**：MCP `readOnlyHint` / `idempotentHint` 在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
- **遗留**：本期未交付 UT / Contract Test / 冒烟脚本 / Micrometer 看板 / README 示例，列入 tasks 遗留项。
