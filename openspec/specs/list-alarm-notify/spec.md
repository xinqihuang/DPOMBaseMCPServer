# list-alarm-notify Specification

## Purpose
查询某条 APM 告警的通知投递记录（渠道 sms/email/webhook、成功或失败、内容快照）；`alarm_data_id` 取自 `list_apm_alarm_data` 返回的 id。
## Requirements
### Requirement: APM 告警通知记录查询

系统 SHALL 提供只读工具 `list_alarm_notify`，调用 APM SDK `ListAlarmNotify`（`POST /v1/apm2/openapi/alarm/data/get-alarm-notify-list`），按 `alarm_data_id` 查询某条 APM 告警的通知投递记录，并返回分页结果 `{ notifications[], total_count }`。

工具 MUST 暴露 5 个参数：`business_id`（头部参数）/ `alarm_data_id` / `page` / `page_size` / `region`。该工具 MUST 为只读（`readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`），不重发通知、不创建或查询通知模板、不做时间窗过滤、不做客户端聚合或排序。

#### Scenario: 入参装配与结果透传
- **WHEN** Agent 传入合法 `alarm_data_id` 及可选 `page` / `page_size` / `region`
- **THEN** 全部入参 SHALL 正确装配到 SDK `AlarmNotifyListRequest` body（仅 `page` / `pageSize` / `alarmDataId` / `region` 4 字段）
- **AND** 返回 `notifications[]` 与上游 `total_count`

#### Scenario: gmt_create 原样透传
- **GIVEN** 上游 `gmt_create` 为上游格式时间字符串（如 `2026-06-10T10:00:05+08:00`，格式未固定）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传该字符串，不做本地时间解析或格式转换

### Requirement: 发现链调用顺序约束

系统 SHALL 将 `list_alarm_notify` 定位为后置于 `list_apm_alarm_data` 的下钻工具：其 `alarm_data_id` 入参来源 MUST 为 `list_apm_alarm_data` 响应中某条告警的 `id` 字段。工具 description MUST 明确该调用顺序，提示 Agent 先调用 `list_apm_alarm_data` 取得 `id`，禁止编造 `alarm_data_id` 入参。

#### Scenario: 先取 id 再查通知
- **GIVEN** Agent 需要核验某条告警是否已送达
- **WHEN** 编排调用链
- **THEN** 系统 SHALL 要求先经 `list_apm_alarm_data` 取得告警 `id`
- **AND** 再以该 `id` 作为 `alarm_data_id` 调用 `list_alarm_notify`

#### Scenario: 禁止编造入参
- **GIVEN** Agent 未持有来自 `list_apm_alarm_data` 的真实 `id`
- **WHEN** 准备调用 `list_alarm_notify`
- **THEN** 系统 SHALL 不接受编造或臆测的 `alarm_data_id`，而应回退到先调用 `list_apm_alarm_data`

### Requirement: business_id 解析

系统 SHALL 支持 `business_id` 头部参数（HTTP `x-business-id`，租户上下文）。当 `business_id` 头部参数为 null 时，系统 SHALL 回落到 `huaweicloud.apm-business-id` 配置默认值并注入 `x-business-id` 头。

#### Scenario: 头部 business_id 回落配置默认值
- **GIVEN** 调用未传 `business_id` 头部参数，但配置了 `huaweicloud.apm-business-id`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 用配置默认值注入 `x-business-id` 头

#### Scenario: 头部与配置默认值均缺失
- **GIVEN** 调用未传 `business_id`，且 `huaweicloud.apm-business-id` 未配置
- **WHEN** 调用工具
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: 输入校验

系统 SHALL 在 service 层校验入参：`alarm_data_id` MUST 必填且 `> 0`；`page` MUST ≥ 1；`page_size` MUST ∈ [1, 100]。校验未通过 MUST 返回 `INVALID_PARAM` 且不发起上游调用。

`alarm_data_id` 类型 MUST 贴齐 SDK 的 `Integer`；当 Agent 回传的告警 `id`（上游为 `Long`）超出 `Integer` 范围时，由调用方负责，本工具不做超出 long→int 安全转换之外的修正。

#### Scenario: alarm_data_id 缺失
- **WHEN** `alarm_data_id == null`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: alarm_data_id 非正
- **WHEN** `alarm_data_id <= 0`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: page_size 越界
- **WHEN** `page_size` < 1 或 > 100
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: page 越界
- **WHEN** `page` < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: FrontAlarmNotifyResult 无损投影

响应 DTO `ApmAlarmNotification` SHALL 无损覆盖 SDK `FrontAlarmNotifyResult` 的全部 8 个字段（`id` / `gmt_create` / `notify_type` / `alarm_rule_id` / `template_id` / `alarm_data_event_id` / `notify_status` / `alarm_content`），类型对齐 SDK（`notify_status` 为可空 `Boolean`，`gmt_create` / `notify_type` / `alarm_content` 为 `String`，其余 id 为 `Long`）。`alarm_content` SHALL 原样保留为字符串，不二次解析为 JSON。任一字段缺失 MUST 导致契约测试失败。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `sdk-samples/apm/list-alarm-notify-response.json`
- **WHEN** 反序列化为 SDK `ListAlarmNotifyResponse` 并经 adapter 映射为 `ApmAlarmNotification`
- **THEN** 8 个字段 SHALL 逐一断言通过
- **AND** 删除任一字段 SHALL 导致编译或断言失败

#### Scenario: notify_status 语义
- **GIVEN** 某通知记录 `notify_status = true`
- **WHEN** 映射为 DTO
- **THEN** 系统 SHALL 表示该通知送达成功；`false` 表示失败；上游未返回时该字段为 null

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 上游错误与超时映射
- **WHEN** 上游返回 5xx 或传输层超时
- **THEN** 系统 SHALL 分别返回 `UPSTREAM_ERROR` / `TIMEOUT`，`retryable=true`

