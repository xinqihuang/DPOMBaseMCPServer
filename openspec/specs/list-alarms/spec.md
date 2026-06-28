# list-alarms Specification

## Purpose
CES 告警历史查询入口：返回华为云基础设施层告警的规则身份、状态、严重度、完整触发条件、五个生命周期时间戳与关联指标，用于事故关联排查；与应用层 `list_apm_alarm_data` 互补。
## Requirements
### Requirement: CES 告警历史查询

系统 SHALL 提供只读工具 `list_alarms`，调用 CES SDK `listAlarmHistories(ListAlarmHistoriesRequest)`，按资源分组 / 告警规则 / 状态 / 级别 / 命名空间 / 时间区间过滤，偏移分页返回告警历史，结果形如 `{ alarms[], total }`。

工具 MUST 暴露过滤参数 `groupId` / `alarmId` / `alarmName` / `alarmStatus` / `alarmLevel` / `namespace` / `from` / `to` 及偏移分页参数 `start` / `limit`。该工具 MUST 为只读（语义 `readOnlyHint=true`），不查询告警规则定义，不做告警 ACK / 静默 / 恢复等写操作，不做客户端聚合 / 排序，不返回告警关联 datapoints（由 `query_ces_metric_data` 负责）。响应中 `namespace` / `metric_name` 由 SDK 嵌套 `MetricInfoResp` 拍平，可能为 null；`total` 由上游透传，可能为 null。

#### Scenario: 按过滤参数透传查询
- **WHEN** Agent 传入任意合法过滤参数组合
- **THEN** 全部入参 SHALL 正确装配到 SDK `ListAlarmHistoriesRequest`
- **AND** 返回 `alarms[]` 与上游 `total`

#### Scenario: 偏移分页
- **GIVEN** 上游分页元数据仅含 `total` 而无 marker
- **WHEN** Agent 指定 `start` 与 `limit`
- **THEN** 系统 SHALL 以偏移方式分页（`start` 为偏移量）
- **AND** 默认 `start=0`、`limit=100`

#### Scenario: 时间参数原样透传
- **GIVEN** `from` / `to` 为毫秒时间戳字符串（长度 [1, 13]）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传，不做本地时间解析或格式转换

#### Scenario: 关联指标可空
- **WHEN** 某告警类型无关联指标（SDK `metric` 为 null）
- **THEN** 响应该条 `namespace` 与 `metric_name` SHALL 为 null
- **AND** 系统 SHALL 不抛空指针异常

### Requirement: 输入校验

系统 SHALL 在 service 层校验入参，校验失败 MUST 返回 `INVALID_PARAM` 且不发起上游调用。校验异常 SHALL 经统一 `ErrorCode` 映射，不得在 DTO 紧凑构造抛 `IllegalArgumentException` 绕过映射。

#### Scenario: limit 越界
- **WHEN** `limit` > 100 或 < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: start 为负
- **WHEN** `start` < 0
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: alarmStatus 取值非法
- **WHEN** `alarmStatus` 不在 `{ok, alarm, insufficient_data, invalid}`（含大小写不符，如 `OK` / `ALARM`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: alarmLevel 越界
- **WHEN** `alarmLevel` 不在 `{1, 2, 3, 4}`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: namespace 正则不通过
- **WHEN** `namespace` 不匹配 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 上游异常映射

系统 SHALL 将 CES SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 SHALL 携带 `error_code` / `error_message` / `upstream_trace_id` / `retryable`。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 服务端错误映射
- **WHEN** 上游返回 5xx
- **THEN** 系统 SHALL 返回 `UPSTREAM_ERROR`，`retryable=true`

#### Scenario: 超时映射
- **WHEN** 上游调用超时
- **THEN** 系统 SHALL 返回 `TIMEOUT`，`retryable=true`

