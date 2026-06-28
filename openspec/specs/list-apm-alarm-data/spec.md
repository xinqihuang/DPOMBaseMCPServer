# list-apm-alarm-data Specification

## Purpose
为智能运维 Agent 提供华为云 APM 应用层告警的检索入口：按时间窗 / 应用 / 关键字 / 级别 / 状态 / 实例 / 环境等条件查询告警记录，返回告警全量元数据（规则身份、级别、实例/IP/region、生命周期时间戳、告警表达式），供后续 `list_alarm_notify` 等工具下钻。与 CES `list_alarms`（基础设施层告警）互补。

## Requirements
### Requirement: APM 告警记录查询

系统 SHALL 提供只读工具 `list_apm_alarm_data`，调用 APM SDK `ListAlarmData`（`POST /v1/apm2/openapi/alarm/data/get-alarm-data-list`），按上游过滤参数检索华为云 APM 告警记录，并返回分页结果 `{ alarms[], total_count }`。

工具 MUST 暴露全部 14 个上游过滤参数（`region` / `app_name` / `business_id_filter` / `monitor_item_id` / `status` / `alarm_level` / `keyword` / `alarm_start_time` / `alarm_end_time` / `collector_id` / `ip_address` / `env_list`，及分页 `page` / `page_size`）加 1 个 `business_id` 头部参数。该工具 MUST 为只读（`readOnlyHint=true`），不做创建 / 修改 / 删除，不做客户端聚合 / 排序，不返回通知动作。

#### Scenario: 按过滤参数透传查询
- **WHEN** Agent 传入任意合法过滤参数组合
- **THEN** 全部入参 SHALL 正确装配到 SDK `AlarmDataListRequest` body
- **AND** 返回 `alarms[]` 与上游 `total_count`

#### Scenario: 时间参数原样透传
- **GIVEN** `alarm_start_time` / `alarm_end_time` 为上游格式字符串（上游为 String，未固定格式）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传，不做本地时间解析或格式转换

### Requirement: business_id 解析与双语义区分

系统 SHALL 区分两个 `business_id` 概念：`business_id`（HTTP `x-business-id` 头，租户上下文）与 `business_id_filter`（body 过滤条件）。当 `business_id` 头部参数为 null 时，系统 SHALL 回落到 `huaweicloud.apm-business-id` 配置默认值。

#### Scenario: 头部 business_id 回落配置默认值
- **GIVEN** 调用未传 `business_id` 头部参数，但配置了 `huaweicloud.apm-business-id`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 用配置默认值注入 `x-business-id` 头

#### Scenario: 头部与配置默认值均缺失
- **GIVEN** 调用未传 `business_id`，且 `huaweicloud.apm-business-id` 未配置
- **WHEN** 调用工具
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: 输入校验

系统 SHALL 在 service 层校验分页参数：`page` MUST ≥ 1；`page_size` MUST ∈ [1, 100]。`app_name` / `keyword` / `status` / `alarm_level` 取值不预校验（上游枚举尚不稳定），仅做非空透传。

#### Scenario: page 越界
- **WHEN** `page` < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: page_size 越界
- **WHEN** `page_size` > 100 或 < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: AlarmDataVO 无损投影

响应 DTO `ApmAlarm` SHALL 无损覆盖 SDK `AlarmDataVO` 的全部 27 个字段，类型对齐 SDK（`collector_id` / `version_number` 为 `Integer`，各时间字段为 `String` 而非 `OffsetDateTime`，`env_list` 为 `List<Long>`）。任一字段缺失 MUST 导致契约测试失败。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `sdk-samples/apm/list-alarm-data-response.json`
- **WHEN** 反序列化为 SDK `ListAlarmDataResponse` 并经 adapter 映射为 `ApmAlarm`
- **THEN** 27 个字段 SHALL 逐一断言通过
- **AND** 删除任一字段 SHALL 导致编译或断言失败

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

