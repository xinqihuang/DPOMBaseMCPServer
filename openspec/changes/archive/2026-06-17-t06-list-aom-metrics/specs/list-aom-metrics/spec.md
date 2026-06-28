## ADDED Requirements

### Requirement: AOM 指标定义发现

系统 SHALL 提供只读工具 `list_aom_metrics`，调用 AOM v2 SDK `ListMetricItems`（`POST /v2/{project_id}/ams/metrics`），按命名空间 / 指标名 / 维度 / 资源 ID（inventoryId）任意组合过滤，发现 Huawei Cloud AOM 应用运维层指标定义，返回分页结果 `{ metrics[], pagination }`。

工具 MUST 暴露入参 `namespace` / `metric_name` / `dimensions` / `inventory_id` / `limit`（默认 100）/ `start`（默认 0）。该工具 MUST 为只读（`readOnlyHint=true`、`idempotentHint=true`、`destructiveHint=false`），不查指标值、不查日志 / 告警、不做跨命名空间合并、不做指标含义解释、不做缓存，且 SDK 类型 MUST NOT 穿透 adapter 边界（公开 DTO 均为自定义 record）。

#### Scenario: namespace 路径透传查询
- **WHEN** Agent 传入 `namespace`（可选叠加 `metric_name` / `dimensions`）
- **THEN** 系统 SHALL 将其装配到 SDK body 的单个 `metricItems` 元素（namespace 转为 `NamespaceEnum`），不设 inventoryId、不设 type
- **AND** 返回 `metrics[]` 与 `pagination`

#### Scenario: inventory_id 路径查询
- **WHEN** Agent 仅传入 `inventory_id`
- **THEN** SDK Request 的 `type` SHALL 为 `inventory`，body 仅设 `inventoryId`，`metricItems` MUST 为 null（不传空 list）

#### Scenario: inventory_id 与 namespace 同时提供时优先级
- **GIVEN** 调用同时提供 `namespace` 与 `inventory_id`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 以 `inventory_id` 优先（走 inventory 路径），不抛错
- **AND** 系统 SHALL 写一条 WARN 日志说明 inventory_id 优先

### Requirement: 输入校验

系统 SHALL 在 service 层校验入参，违反任一规则 MUST 抛 `InvalidParamException`（映射为 `INVALID_PARAM`）且 MUST NOT 发起上游 SDK 调用。校验规则：`namespace` 或 `inventory_id` 至少提供一个；`limit` MUST ∈ [1, 1000]；`start` MUST ≥ 0；若提供 `dimensions`，每个元素的 `name` 与 `value` 都 MUST 非空；`namespace` 若提供 MUST 匹配 `^(PAAS\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_.]{2,63})$`；`inventory_id` 若提供 MUST 匹配 `^(host|application|instance|container|process|network|storage|volume)_[A-Za-z0-9-]+$`。错误消息 SHOULD 指明不合规项。

#### Scenario: namespace 与 inventory_id 均缺失
- **WHEN** 调用既无 `namespace` 也无 `inventory_id`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: limit 越界
- **WHEN** `limit` > 1000 或 `limit` < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: start 为负
- **WHEN** `start` < 0
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: dimensions 元素 value 为空
- **WHEN** `dimensions` 含 `{name:"appName", value:""}`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: namespace 格式非法
- **WHEN** `namespace` 为 CES 风格值（如 `syc.ecs`）不匹配 AOM 命名空间正则
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: inventory_id resType 不在枚举
- **WHEN** `inventory_id` 的 resType 不在 `host/application/instance/container/process/network/storage/volume`（如 `bogus_xxx`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

### Requirement: 分页与 metric 元数据投影

系统 SHALL 将 SDK 响应投影为 `metrics[]`（每条含 `namespace` / `metric_name` / `unit` / `dimensions[].{name,value}` / `dimension_value_hash`）与 `pagination`（`count` / `total` / `offset` / `next_token` / `has_more`）。`next_token` MUST 为 Integer 类型（透传 SDK `MetaDataSeries.getNextToken()`，区别于 CES 的 String marker）；`has_more` SHALL 计算为 `next_token != null && count > 0`。`dimension_value_hash` SHALL 由 SDK `getDimensionvaluehash()` 原样透传。SDK 在 limit / start 字段为 String，系统 MUST 用 `String.valueOf(...)` 转换。

#### Scenario: 空结果
- **WHEN** SDK 返回空 metrics
- **THEN** `metrics` SHALL 为 `[]`，`pagination.count` SHALL 为 0，`has_more` SHALL 为 false

#### Scenario: 有下一页
- **GIVEN** SDK 返回 `next_token != null` 且 `count > 0`
- **WHEN** 投影响应
- **THEN** `pagination.next_token` SHALL 原样透传，`has_more` SHALL 为 true

#### Scenario: 无下一页
- **WHEN** SDK 返回 `next_token = null`
- **THEN** `pagination.has_more` SHALL 为 false

#### Scenario: limit 字符串转换
- **WHEN** 业务侧 `limit=100`
- **THEN** SDK Request 的 `limit` 字段 SHALL 为 String `"100"`

#### Scenario: dimension_value_hash 透传
- **WHEN** SDK 返回的 metric 含 `dimensionvaluehash`
- **THEN** 输出 DTO 的 `dimension_value_hash` SHALL 携带该值

### Requirement: 上游异常映射

系统 SHALL 将 AOM SDK 异常经 `SdkExceptionMapper`（按 HTTP status）映射为统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 SHALL 携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。HTTP 200 时 body 中的 `errorCode`（如 `SVCSTG_AMS_2000000` 成功码）MUST NOT 被当作业务错误。

#### Scenario: 限流映射
- **WHEN** 上游返回 HTTP 429
- **THEN** 系统 SHALL 经重试后返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 HTTP 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`，不重试

#### Scenario: 服务端错误映射
- **WHEN** 上游返回 HTTP 5xx
- **THEN** 系统 SHALL 经重试后返回 `UPSTREAM_ERROR`，`retryable=true`

#### Scenario: 超时映射
- **WHEN** SDK 抛 RequestTimeoutException / 连接异常
- **THEN** 系统 SHALL 返回 `TIMEOUT`，`retryable=true`
