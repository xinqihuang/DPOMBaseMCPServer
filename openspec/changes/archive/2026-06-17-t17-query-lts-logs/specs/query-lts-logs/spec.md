## ADDED Requirements

### Requirement: LTS 日志检索

系统 SHALL 提供只读工具 `query_lts_logs`，调用 LTS SDK `listLogs`（`POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query`），在给定 `log_group_id` + `log_stream_id` 下按时间区间 / 关键字 / 标签 / SQL 检索日志，返回 `{ count, logs[], is_query_complete, analysis_logs[] }`。

工具 MUST 暴露 17 个参数：必填 `log_group_id` / `log_stream_id`，可选 `start_time_millis` / `end_time_millis` / `labels` / `keywords` / `query` / `is_analysis_query` / `is_count` / `limit` / `is_desc` / `highlight` / `is_iterative` / `search_type` / `line_num` / `cursor_time` / `scroll_id`。该工具 MUST 为只读（`readOnlyHint=true`，`destructiveHint=false`，`idempotentHint=true`），不返回 trace / metric / 拓扑，不做客户端结果分桶或高亮拼装。

#### Scenario: 全合法参数透传查询
- **WHEN** Agent 传入合法的 `log_group_id` + `log_stream_id` 及任意合法检索参数组合
- **THEN** 全部入参 SHALL 字段对齐装配到 SDK 请求并委托 adapter 调用
- **AND** 返回值 SHALL 原样透传 `count` / `logs[]` / `is_query_complete` / `analysis_logs[]`

#### Scenario: 分析模式
- **GIVEN** `is_analysis_query=true` 且提供 SQL `query`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 通过 `analysis_logs` 返回 SQL 分析结果
- **AND** `logs` 可能为空

#### Scenario: 时间窗为 UTC 毫秒
- **GIVEN** `start_time_millis` / `end_time_millis` 为 UTC 毫秒（long）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 将其字符串化后透传到上游 `start_time` / `end_time`，不做格式转换或本地时区解析

### Requirement: 输入校验

系统 SHALL 在 service 层执行输入校验，校验失败 MUST 返回 `INVALID_PARAM` 且不发起上游调用。校验规则：`log_group_id` / `log_stream_id` 必填且非空白；`start_time_millis` 与 `end_time_millis` 同时给时要求 `start < end`；`limit` 非 null 时必须 ∈ [1, 5000]；`search_type` 非 null 时必须为 `forwards` / `backwards`；`line_num` 与 `cursor_time` 必须同时给或同时不给。

#### Scenario: request 为 null
- **WHEN** 传入的 request 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 必填 id 空白
- **WHEN** `log_group_id` 或 `log_stream_id` 为空白
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，且不调用 adapter

#### Scenario: 时间窗非法
- **WHEN** `start_time_millis >= end_time_millis`（二者均非 null）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: limit 越界
- **WHEN** `limit = 0` 或 `limit = 5001`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: search_type 非法
- **WHEN** `search_type = "random"`（非 `forwards` / `backwards`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 游标单边给
- **WHEN** `line_num` 给出但 `cursor_time` 缺失（或反之）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 游标分页与发现链衔接

系统 SHALL 支持 `line_num` + `cursor_time` 成对游标分页与 `scroll_id` 分页。响应中每条 `logs[].line_num`（纳秒时间戳格式）MUST 可与 `cursor_time` 配对作为下一页游标，也可作为 `query_lts_log_context` 的入参定位目标日志。Agent 调用分页或下钻时 MUST 使用上一次响应实际返回的 `line_num` / `cursor_time`，禁止编造游标入参。

#### Scenario: 翻页使用上一页尾行游标
- **GIVEN** 上一页响应的尾行 `line_num` 与对应 `cursor_time`
- **WHEN** Agent 以该 `line_num` + `cursor_time` + `search_type=forwards|backwards` 再次调用
- **THEN** 系统 SHALL 返回下一页日志

#### Scenario: 结果未完成需继续分页
- **WHEN** 响应 `is_query_complete=false`
- **THEN** 系统 SHALL 表示结果被截断或仍在处理，Agent 需继续分页

### Requirement: 上游异常映射

系统 SHALL 将 LTS SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 HTTP 429 / SDK throttling
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 HTTP 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 服务端错误与超时映射
- **WHEN** 上游返回 HTTP 5xx 或调用超时
- **THEN** 系统 SHALL 分别返回 `UPSTREAM_ERROR` / `TIMEOUT`，`retryable=true`
