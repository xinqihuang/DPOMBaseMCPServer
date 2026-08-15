# delete-notification-masks Specification

> 安全边界：该破坏性写工具默认不注册。仅当 `action-enabled` profile 与
> `dpom.mcp.write-tools-enabled=true` 同时启用时才可发现；DPOMAgent 禁止启用或调用。

## Purpose
批量删除 CES 告警通知屏蔽规则（写操作）：按 mask id 列表删除不再需要的屏蔽，支持部分删除（输入 N 个、上游实删 M 个）的结果反馈，用于屏蔽期结束后及时恢复告警通知。
## Requirements
### Requirement: 批量删除告警通知屏蔽规则

系统 SHALL 提供写工具 `delete_notification_masks`，调用 CES v2 SDK `BatchDeleteNotificationMasks`，按 `notification_mask_ids` 批量删除 CES 告警通知屏蔽规则（一次最多 100 条），并返回 `{ notification_mask_ids[] }`（上游实际删除成功的 ID 子集，可能为空但 MUST NOT 为 null）。工具不按条件删除（须先 `list_notification_masks` 拿 ID），不软删除，不级联清理告警规则本身。

#### Scenario: 合法 ID 列表删除成功
- **GIVEN** `notification_mask_ids` 长度在 [1, 100] 且每项非空白
- **WHEN** 调用工具
- **THEN** ID 列表 SHALL 装配到 SDK `BatchDeleteNotificationMasksRequest` body
- **AND** 返回上游实际删除成功的 ID 子集

#### Scenario: 发现链顺序与禁止编造入参
- **GIVEN** Agent 需删除某屏蔽规则
- **WHEN** 准备调用 `delete_notification_masks`
- **THEN** Agent SHALL 先调用 `list_notification_masks` 取得真实 `notification_mask_id`
- **AND** MUST NOT 编造 `notification_mask_id`，所有 ID 须来自上游真实返回

### Requirement: delete 输入校验

系统 SHALL 在 service 层显式校验长度上下限，不依赖上游限制：`notification_mask_ids` 为 null / 长度 0 / 长度 > 100 SHALL 返回 `INVALID_PARAM`；任一 ID 为 null 或空白 SHALL 返回 `INVALID_PARAM`。校验失败不发起上游调用。

#### Scenario: 超过上限
- **WHEN** `notification_mask_ids` 长度为 101
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 adapter

#### Scenario: 空列表或含空白项
- **WHEN** `notification_mask_ids` 为空列表，或列表含空字符串 / 空白项
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: delete 幂等语义与部分删除

系统 SHALL 将 `delete_notification_masks` 标记为破坏性（`destructiveHint=true`）且幂等（`idempotentHint=true`）：删除已不存在的 ID 上游不抛错，仅从返回列表中剔除。系统 MUST NOT 将「返回列表长度 < 入参长度」视为失败；adapter 在 SDK 返回 null `notificationMaskIds` 时 SHALL 兜底为空 List，不 NPE。

#### Scenario: 部分 ID 已不存在
- **GIVEN** 入参含若干已不存在的 ID
- **WHEN** 调用工具
- **THEN** 系统 SHALL 返回实际删除成功的 ID 子集
- **AND** SHALL NOT 报错或触发重试

#### Scenario: SDK 返回 null 列表
- **WHEN** 上游 SDK 响应 `notificationMaskIds` 为 null
- **THEN** adapter SHALL 兜底返回空 List，DTO `notification_mask_ids` 不为 null

### Requirement: delete 上游异常映射

系统 SHALL 通过 `HuaweiCloudInvocation` 将 SDK 异常统一映射到 `ErrorCode`，失败响应携带 `retryable` 与 `upstream_trace_id`。映射：429→`UPSTREAM_THROTTLED`(true)、401/403→`UPSTREAM_AUTH_FAILED`(false)、5xx→`UPSTREAM_ERROR`(true)、超时→`TIMEOUT`(true)、序列化/未分类→`INTERNAL`(false)。幂等性保证 5xx / 超时重试安全。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 经 3 次指数退避重试后返回 `UPSTREAM_THROTTLED`，`retryable=true`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

