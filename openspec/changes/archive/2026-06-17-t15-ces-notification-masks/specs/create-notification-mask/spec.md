## ADDED Requirements

### Requirement: 创建告警通知屏蔽规则

系统 SHALL 提供写工具 `create_notification_mask`，调用 CES v2 SDK `BatchUpdateNotificationMasks`，创建一条 CES 告警通知屏蔽规则，并返回 `{ relation_ids[], notification_mask_id }`。工具 MUST 支持五种 `relation_type`（`ALARM_RULE` / `RESOURCE` / `RESOURCE_POLICY_NOTIFICATION` / `RESOURCE_POLICY_ALARM` / `EVENT.SYS`）与三种 `mask_type`（`START_END_TIME` / `FOREVER_TIME` / `CYCLE_TIME`）。工具一次仅创建一条，不批量，不做客户端去重 / 幂等键管理。

#### Scenario: 全合法请求创建成功
- **GIVEN** `mask_name` 匹配正则、`relation_type=ALARM_RULE` 且 `relation_ids` 非空、`mask_type=START_END_TIME` 且四个时间字段齐备且格式正确
- **WHEN** 调用工具
- **THEN** 全部入参 SHALL 正确装配到 SDK `BatchUpdateNotificationMasksRequest` body
- **AND** 返回上游生效的 `relation_ids` 与新建的 `notification_mask_id`

#### Scenario: 时间字段格式区分透传
- **GIVEN** `start_date` / `end_date` 为 `yyyy-MM-dd`，`start_time` / `end_time` 为 `HH:mm:ss`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 将 `start_date` / `end_date` 经 `LocalDate.parse` 转 SDK `LocalDate`
- **AND** 将 `start_time` / `end_time` 作为 String 原样透传

### Requirement: create 输入校验

系统 SHALL 在 service 层校验：`mask_name` MUST 匹配 `^[A-Za-z0-9_\-一-龥]{1,64}$`；`relation_type` / `mask_type` / `resource_level` 给值 MUST 在各自枚举集（`relation_type` 用 `ALLOWED_RELATION_TYPES`，含 `EVENT.SYS`、不含 `DEFAULT`）；`relation_type=ALARM_RULE` 时 `relation_ids` MUST 非空；`relation_type=RESOURCE` 时 `resources` MUST 非空；`mask_type∈{START_END_TIME, CYCLE_TIME}` 时四个时间字段 MUST 齐备且格式正确。任一校验失败 SHALL 返回 `INVALID_PARAM` 且不发起上游调用。

#### Scenario: mask_name 含空格
- **WHEN** `mask_name` 含空格或其他非法字符
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 adapter

#### Scenario: 条件必填缺失
- **WHEN** `relation_type=ALARM_RULE` 但 `relation_ids` 为空，或 `relation_type=RESOURCE` 但 `resources` 为空
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 时间字段缺失或格式错
- **WHEN** `mask_type=START_END_TIME` 缺 `start_date`，或 `start_date` 格式为 `2026/06/02`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不触发 SDK `DateTimeParseException`

### Requirement: create 非幂等语义

系统 SHALL 将 `create_notification_mask` 标记为非幂等（`idempotentHint=false`）：同名重复调用上游会创建两条不同 `notification_mask_id` 的规则。Agent SHALL 在调用前先 `list_notification_masks` 检查同名规则，MUST NOT 依赖工具做去重。

#### Scenario: 同名重复调用产生两条规则
- **GIVEN** 已存在同名屏蔽规则
- **WHEN** 再次以相同 `mask_name` 调用
- **THEN** 上游 SHALL 创建一条新的、ID 不同的屏蔽规则
- **AND** 工具 SHALL NOT 报错或自动去重

### Requirement: create 上游异常映射

系统 SHALL 通过 `HuaweiCloudInvocation` 将 SDK 异常统一映射到 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`。映射：429→`UPSTREAM_THROTTLED`(true)、401/403→`UPSTREAM_AUTH_FAILED`(false)、5xx→`UPSTREAM_ERROR`(true)、超时→`TIMEOUT`(true)、序列化/未分类→`INTERNAL`(false)。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 经 3 次指数退避重试后返回 `UPSTREAM_THROTTLED`，`retryable=true`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`，不重试
