## ADDED Requirements

### Requirement: 取回超长字段全文

系统 SHALL 提供只读工具 `show_clob_detail`，调用 APM v1 `ShowClobDetail` 按 `clob_id` + `env_id` 取回事件详情中以 clob 引用存储的超长字段全文（完整异常堆栈 / 完整 SQL）。这是 trace 根因诊断链的终点。`clob_id` MUST 来自 `show_event_detail` / `show_trace_events` 响应的 tags/attachment 中出现的 clob 引用（AGENTS.md §4.3(b)），禁止编造。实时数据，MUST 不缓存。

#### Scenario: 按 clob_id 取回全文
- **WHEN** 提供有效 `env_id` 与 `clob_id`（`business_id` 可选，为 null 回落 `huaweicloud.apm-business-id` 配置）
- **THEN** 系统 SHALL 返回 `clob_string`（全文）
- **AND** `business_id` 装配到 header `x-business-id`，`env_id` / `clob_id` 装配到 body

#### Scenario: 必填缺失
- **WHEN** `env_id` 为 null 或 `clob_id` 空白
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: ShowClobDetail 无损投影

响应 DTO `ApmClobDetailResponse` SHALL 覆盖 SDK `ShowClobDetailResponse` 的 `clob_string`（String，全文）——1 字段全量。

#### Scenario: 契约测试断言
- **GIVEN** 真实样本响应
- **WHEN** 反序列化并经 adapter 映射
- **THEN** `clob_string` SHALL 断言通过，且请求侧 header/body 装配（含 business_id 配置回落）正确
