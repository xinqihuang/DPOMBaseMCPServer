# query-lts-log-context Specification

## Purpose
取 LTS 某目标日志行的上下文（前后 N 条），重建事发时刻的事件序列；承接 `query_lts_logs` 命中的行。
## Requirements
### Requirement: LTS 日志上下文查询

系统 SHALL 提供只读工具 `query_lts_log_context`，调用 LTS SDK `listLogContext`（`POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context`），以目标日志为中心拉取其前 N（`backwards_size`）+ 后 N（`forwards_size`）条日志，返回 `{ logs[], total_count, backwards_count, forwards_count, is_query_complete }`。

工具 MUST 暴露 7 个参数：`log_group_id` / `log_stream_id` / `line_num` / `cursor_time` / `backwards_size` / `forwards_size` / `scroll_id`。该工具 MUST 为只读（`readOnlyHint=true`、`idempotentHint=true`），不接受时间区间 / keywords / query / SQL，不做客户端排序，不做跨流上下文。其语义为 `query_lts_logs` 的后置工具。

#### Scenario: 首次模式按目标日志拉取上下文
- **GIVEN** Agent 已通过 `query_lts_logs` 得到目标日志的 `line_num` 与 `cursor_time`
- **WHEN** 传入 `log_group_id` + `log_stream_id` + `line_num` + `cursor_time` + 合法 sizes
- **THEN** service 收到字段对齐的 request 并委托 adapter
- **AND** 返回值原样透传上游 `logs[]` 与各计数字段

#### Scenario: 时间与游标参数原样透传
- **GIVEN** `cursor_time` 为目标日志的 `__time__` 字符串、`line_num` 为字符串序号
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传，不做本地时间解析、格式转换或数值比较

### Requirement: 发现链调用顺序约束

系统 SHALL 将 `query_lts_log_context` 定位为 `query_lts_logs` 的后置工具：tool description MUST 提示 "use this after query_lts_logs"，引导 Agent 先用 `query_lts_logs` 命中目标日志、取得其 `line_num` 与 `cursor_time`，再调用本工具。Agent MUST NOT 编造 `line_num` / `cursor_time` 入参，必须取自上一次 `query_lts_logs` 响应。

#### Scenario: 续翻页复用上次响应游标
- **GIVEN** 上一次响应 `is_query_complete=false`
- **WHEN** Agent 需要继续翻页
- **THEN** Agent SHALL 复用上一次响应尾行的 `line_num` + `cursor_time`（或 `scroll_id`）作为新目标点
- **AND** 系统 MUST NOT 要求 Agent 自行编造游标值

### Requirement: 输入校验

系统 SHALL 在 service 层校验入参，校验失败 MUST 返回 `INVALID_PARAM` 且不发起上游调用。规则：`log_group_id` / `log_stream_id` 必填且非空白；模式互斥（首次模式需 `line_num` 与 `cursor_time` 都非空，翻页模式需 `scroll_id` 非空）；`line_num` 与 `cursor_time` 不能只给一个（除非走 scroll 模式且两者都不给）；`backwards_size` / `forwards_size` 非 null 时取值 `[0, 500]`；`backwards_size` 与 `forwards_size` 不能同时为 0。

#### Scenario: request 为 null
- **WHEN** `request == null`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 必填项空白
- **WHEN** `log_group_id` 或 `log_stream_id` 为空白
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 首次模式与翻页模式均不满足
- **WHEN** 既无 `line_num`+`cursor_time` 也无 `scroll_id`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，提示 "either (line_num + cursor_time) or scroll_id is required"

#### Scenario: line_num 与 cursor_time 单边给定
- **GIVEN** 未提供 `scroll_id`
- **WHEN** 只给 `line_num` 不给 `cursor_time`（或反之）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，提示 "line_num and cursor_time must be provided together"

#### Scenario: size 越界
- **WHEN** `backwards_size` 或 `forwards_size` 非 null 且 < 0 或 > 500
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 前后向同时为 0
- **WHEN** `backwards_size = 0` 且 `forwards_size = 0`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，提示 "at least one of backwards_size / forwards_size must be > 0"

#### Scenario: 纯 scroll 模式两者皆空合法
- **GIVEN** `scroll_id` 非空且 `line_num` / `cursor_time` 都为空
- **WHEN** 调用工具
- **THEN** 系统 SHALL 委托 adapter，不抛校验异常

### Requirement: 上游异常映射

系统 SHALL 将 LTS SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `error_code` / `error_message` / `retryable` / `upstream_trace_id`（华为云 `X-Request-Id`，可空）。映射：429→`UPSTREAM_THROTTLED`(retryable)、401/403→`UPSTREAM_AUTH_FAILED`(不可重试)、5xx→`UPSTREAM_ERROR`(retryable)、超时→`TIMEOUT`(retryable)、未分类→`INTERNAL`(不可重试)。

#### Scenario: 限流映射
- **WHEN** 上游返回 429 / SDK throttling
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

