# get-service-topology Specification

## Purpose
按 trace_id 还原调用图拓扑（服务节点 + 带 client/server 计时的有向边），定位时间与错误集中在哪个节点/边（诊断链第 2 步）。
## Requirements
### Requirement: 调用链拓扑查询

系统 SHALL 提供只读工具 `get_service_topology`，按入参 `traceId` 调用 APM SDK `showTopology`，返回该 trace 重建后的调用链拓扑 `{ global_trace_id, nodes[], lines[] }`。`nodes[]` 每项含 `node_id`（Long） / `node_name` / `hint`；`lines[]` 每项含 `start_node_id` / `end_node_id`（Long） / `span_id` / `client_start_time` / `client_time_used` / `server_start_time` / `server_time_used`（均为 UTC 毫秒 Long） / `hint`。该工具 MUST 为只读（`readOnlyHint=true` / `idempotentHint=true`），不做 trace 搜索、拓扑过滤 / 子图切分、span tags / 异常详情，也不做分页。

#### Scenario: 合法 traceId 返回拓扑
- **WHEN** Agent 传入合法 `traceId`
- **THEN** 系统 SHALL 用 `ShowTopologyRequest.withTraceId(traceId)` 调用上游 `showTopology`
- **AND** 返回 `global_trace_id` 与节点 `nodes[]`、有向边 `lines[]`

#### Scenario: 作为 query_traces 的后置下钻
- **GIVEN** Agent 先用 `query_traces` 检索得到一条 `traceId`
- **WHEN** 以该 `traceId` 调用 `get_service_topology`
- **THEN** 系统 SHALL 返回该单一 trace 重建的图结构，而非服务级常驻拓扑

#### Scenario: 不接受 business_id 头
- **WHEN** 调用 `showTopology`
- **THEN** 系统 SHALL NOT 注入 `x-business-id` 头（该接口不接受此头）

### Requirement: traceId 输入校验

系统 SHALL 在 service 层对 `traceId` 做必填校验：`traceId` 为 null / 空串 / 仅空白时返回 `INVALID_PARAM`（"trace_id is required"），且不发起上游调用。`traceId` 的格式不预校验，交由上游 APM 判定。

#### Scenario: traceId 为 null
- **WHEN** `traceId` 为 null（或 request 为 null）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

#### Scenario: traceId 为空串或仅空白
- **WHEN** `traceId` 为 `""` 或 `"   "`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: 嵌套对象拍平与空值兜底

系统 SHALL 将 SDK 嵌套的 `TraceTopologyLineInfo`（clientInfo / serverInfo）拍平为 `client_start_time` / `client_time_used` / `server_start_time` / `server_time_used` 四个字段；当 `clientInfo` 或 `serverInfo` 为 null 时，对应字段 SHALL 为 null 且 MUST NOT 抛 NPE。响应中的 `nodes[]` / `lines[]` SHALL 始终非 null：上游返回 null 时用 `Collections.emptyList()` 兜底。

#### Scenario: clientInfo / serverInfo 缺失
- **GIVEN** 某条边的 `getClientInfo()` 返回 null
- **WHEN** adapter 映射该边
- **THEN** 系统 SHALL 将 `client_start_time` / `client_time_used` 置为 null
- **AND** MUST NOT 抛 NullPointerException

#### Scenario: nodes / lines 上游为 null
- **GIVEN** 上游响应的 `nodes` 或 `lines` 为 null
- **WHEN** adapter 映射响应
- **THEN** 系统 SHALL 用空列表兜底，使返回的 `nodes[]` / `lines[]` 非 null

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401 / 403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 上游错误与超时映射
- **WHEN** 上游返回 5xx 或传输层超时
- **THEN** 系统 SHALL 分别返回 `UPSTREAM_ERROR` / `TIMEOUT`，`retryable=true`

### Requirement: 非功能要求

系统 SHALL 复用 `apm-readonly` RateLimiter（与 `query_traces` 共享 10 QPS）；仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避重试（200ms / 800ms / 3.2s）；SDK 传输层超时 10s；输出 Micrometer `mcp_tool_invocation{tool="get_service_topology"}` 指标与含 traceId 的 `apm.showTopology start` INFO 日志。

#### Scenario: 限流域复用
- **WHEN** `get_service_topology` 与 `query_traces` 并发调用
- **THEN** 二者 SHALL 共享 `apm-readonly` 的 10 QPS 配额

#### Scenario: 可观测埋点
- **WHEN** 工具被调用
- **THEN** 系统 SHALL 记录 `mcp_tool_invocation{tool="get_service_topology"}` 指标
- **AND** adapter SHALL 输出含 traceId 的 INFO 日志

