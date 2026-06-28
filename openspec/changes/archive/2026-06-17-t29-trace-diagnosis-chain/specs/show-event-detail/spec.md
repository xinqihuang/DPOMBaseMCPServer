## ADDED Requirements

### Requirement: 获取单个调用链事件详情

系统 SHALL 提供只读工具 `show_event_detail`，调用 APM v1 `ShowEventDetail` 按四元组 `(trace_id, span_id, event_id, env_id)` 返回单个事件的完整详情，其 `tags` 含异常类名 / message / SQL / HTTP 状态等根因数据。四个入参 MUST 全部来自 `show_trace_events` 的真实响应（发现真值转发，AGENTS.md §4.3(b)），禁止凭先验编造。实时数据，MUST 不缓存。

#### Scenario: 按四元组返回详情
- **WHEN** 四个入参均来自 `show_trace_events` 响应且非空
- **THEN** 系统 SHALL 返回 `event_info`（`ApmSpanEvent` 单对象，tags/attachment 内容更全）

#### Scenario: 任一入参缺失
- **WHEN** `trace_id` / `span_id` / `event_id` / `env_id` 任一缺失或空白
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 详情响应复用 SpanEventInfo 投影

`event_info` SHALL 复用 `show_trace_events` 的 `ApmSpanEvent` 38 字段 record（单对象形态）；详情形态下超长字段（完整堆栈 / SQL）以 clob 引用出现在 `tags` / `attachment` 中，交由 `show_clob_detail` 取全文。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实样本 `event_info`
- **WHEN** 反序列化并经 adapter 映射
- **THEN** `ApmSpanEvent` 全字段 SHALL 断言通过，且请求侧四 query 参数装配正确
