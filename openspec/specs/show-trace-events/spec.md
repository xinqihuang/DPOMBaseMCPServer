# show-trace-events Specification

## Purpose
按 trace_id 获取一个 trace 的全部调用链事件序列（方法/SQL/远程调用，含耗时与错误标记），从'哪个组件慢/错'下钻到'组件内哪一步'（诊断链第 3 步）。
## Requirements
### Requirement: 获取 trace 全部调用链事件

系统 SHALL 提供只读工具 `show_trace_events`，调用 APM v1 `ShowTraceEvents` 按 `trace_id` 返回一个 trace 的全部调用链事件序列（每个 span 内部的方法调用 / SQL / 远程调用，含耗时、状态、错误标记与 `event_id`）。这是从"哪个组件慢/错"下钻到"组件内哪一步"的桥梁，调用链：`query_traces → get_service_topology → show_trace_events → show_event_detail → show_clob_detail`。`trace_id` MUST 来自告警载荷或 `query_traces` 响应，禁止编造。实时数据，MUST 不缓存。

#### Scenario: 按 trace_id 返回事件序列
- **WHEN** 提供合法 `trace_id`
- **THEN** 系统 SHALL 返回 `span_event_list[]`，每项含 `span_id` / `event_id`（或 `id`）/ `env_id` / `tags` / `has_error` 等下钻所需字段

#### Scenario: trace_id 空白
- **WHEN** `trace_id` 为空白
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: SpanEventInfo 无损投影

响应 DTO `ApmSpanEvent` SHALL 无损覆盖 SDK `SpanEventInfo` 全部 38 个字段（与 `show_event_detail` 共享同一 record），SDK 原样的混合命名（如 `next_spanId`）照抄；`discard` 映射为 `List<ApmDiscardInfo>`（含原样驼峰 `totalTime`）；`tags` / `attachment` 为 Map，超长内容以 clob 引用出现（key 形如 `*_clob_id`），用 `show_clob_detail` 取全文。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实样本（全字段事件 + 最小字段事件各一条）
- **WHEN** 反序列化并经 adapter 映射
- **THEN** `ApmSpanEvent` 38 字段与 `ApmDiscardInfo` 3 字段 SHALL 逐一断言通过，漂移即 fail

