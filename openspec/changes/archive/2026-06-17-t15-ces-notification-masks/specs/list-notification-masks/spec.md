## ADDED Requirements

### Requirement: 分页查询告警通知屏蔽规则

系统 SHALL 提供只读工具 `list_notification_masks`，调用 CES v2 SDK `ListNotificationMasks`，分页 + 多条件过滤查询 CES 告警通知屏蔽规则，返回 `{ notification_masks[], count }`。`notification_masks` 可能为空但 MUST NOT 为 null；`count` 为上游返回总条数（`Integer`，可能为 null），用于客户端推断是否还有下一页。工具 MUST 为只读（`readOnlyHint=true`），不返回屏蔽生效历史，不跨 region / projectId，不做客户端缓存。

#### Scenario: 仅默认分页查询
- **GIVEN** 仅 `offset` / `limit` 取默认值（0 / 100），无任何过滤字段
- **WHEN** 调用工具
- **THEN** adapter SHALL 收到 `offset=0` / `limit=100` 且不设置 body（`bodyHasValue=false`）
- **AND** 返回屏蔽规则列表与上游 `count`

#### Scenario: 带过滤字段查询
- **WHEN** 传入 `mask_name` 等过滤字段
- **THEN** adapter SHALL 设置对应 body 字段且 `bodyHasValue=true`
- **AND** 过滤字段正确装配到 SDK `ListNotificationMaskRequestBody`

### Requirement: list 输入校验与枚举集差异

系统 SHALL 在 service 层校验：`offset` MUST ∈ [0, 10000]；`limit` MUST ∈ [1, 100]；`sort_key` / `sort_dir` / `relation_type` / `resource_level` / `mask_status` 给值则 MUST 在各自枚举集。`relation_type` 用 `ALLOWED_LIST_RELATION_TYPES`（含 `DEFAULT`、不含 `EVENT.SYS`），与 create 的枚举集**不同**。全部过滤字段缺省时允许。校验失败 SHALL 返回 `INVALID_PARAM` 且不发起上游调用。

#### Scenario: 分页越界
- **WHEN** `offset` < 0 或 > 10000，或 `limit` > 100 或 < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: relation_type 用了 create 专属值
- **WHEN** `relation_type=EVENT.SYS`（list 不允许）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: sort_key 非法
- **WHEN** `sort_key` 给值但不在 `create_time` / `update_time` 集合内
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: list 响应枚举透传

系统 SHALL 将响应中各枚举字段（`relation_type` / `mask_type` / `mask_status` / `resource_level` 等）经 `.getValue()` 转 String 后放入 DTO，以便与 create 工具的入参对照，避免 Jackson 序列化输出枚举对象嵌套结构污染 MCP 契约。

#### Scenario: 枚举字段转字符串
- **GIVEN** 上游返回含枚举字段的多条屏蔽规则
- **WHEN** adapter 映射为 DTO
- **THEN** 各枚举字段 SHALL 为 `.getValue()` 字符串
- **AND** `count` SHALL 原样透传

### Requirement: list 限流与上游异常映射

系统 SHALL 让 `list_notification_masks` 走 `ces-readonly` 限流域（10 QPS，与既有只读工具共享），并将 SDK 异常统一映射到 `ErrorCode`，失败响应携带 `retryable` 与 `upstream_trace_id`。映射：429→`UPSTREAM_THROTTLED`(true)、401/403→`UPSTREAM_AUTH_FAILED`(false)、5xx→`UPSTREAM_ERROR`(true)、超时→`TIMEOUT`(true)、序列化/未分类→`INTERNAL`(false)。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 经 3 次指数退避重试后返回 `UPSTREAM_THROTTLED`，`retryable=true`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`
