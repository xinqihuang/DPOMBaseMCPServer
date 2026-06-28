# show-apm-monitor-item-view-config Specification

## Purpose
APM 趋势发现链第 2 步：按 (collector_id, env_id) 返回上游定义的 view 列表（含真实 metric_set/view_type/function），供 `show_apm_trend` 原样取用；collector_id 取自第 1 步。
## Requirements
### Requirement: 采集器视图清单发现查询

系统 SHALL 提供只读工具 `show_apm_monitor_item_view_config`，调用 APM SDK `ShowMonitorItemViewConfig`，按 `collector_id` + `env_id` 查出该采集器的真实视图清单，返回 `{ title, collector_name, view_row_list[] }`，其中每个 view 含 `metric_set` 与 `field_item_list`（含 `function` / `as`）。该工具 MUST 为只读，无时间参数，不藏 `function`、不做 viewKey / 静态目录、不做服务端代编排。

#### Scenario: 按 collector_id + env_id 查视图清单
- **WHEN** Agent 传入合法 `collector_id` 与 `env_id`
- **THEN** 系统 SHALL 装配 `ShowMonitorItemViewConfigRequest{collectorId, envId, xBusinessId}` 调用上游
- **AND** 返回 `view_row_list[]`，每个 view 含 `collector_name` / `metric_set` / `view_type` / `field_item_list`（元素含 `function` / `as`）

### Requirement: 发现链调用顺序与禁止编造入参

系统 SHALL 要求 Agent 按发现链顺序使用本工具：`show_env_monitor_items` → `show_apm_monitor_item_view_config` → 选一个 view → `show_apm_trend`。`collector_id` MUST 来自 `show_env_monitor_items` 的运行时响应（env 局部、会变），禁止硬编码或跨 env 复用。Agent 喂给 `show_apm_trend` 的 `view_config` MUST 原样取自本工具返回的某个 `view_list[]` 条目，禁止凭先验编造 `collector_name` / `metric_set` / `function`。

#### Scenario: collector_id 必来自前置发现工具
- **GIVEN** Agent 已调 `show_env_monitor_items(env_id)` 取得某监控项的 `collector_id`
- **WHEN** 调用 `show_apm_monitor_item_view_config(collector_id, env_id)`
- **THEN** 系统 SHALL 返回该采集器在该 env 下的真实视图清单
- **AND** 该 `collector_id` SHALL 不被跨 env 复用（同一 collector_id 跨 env 含义不同）

#### Scenario: view 原样转发给 show_apm_trend
- **GIVEN** 本工具返回的某个 `view_list[]` 条目
- **WHEN** Agent 将其原样填入 `show_apm_trend` 的 `view_config`
- **THEN** 字段 SHALL 因 `ViewBase` 与 `TrendView` 同构而直接装配
- **AND** Agent SHALL NOT 自行编造 `collector_name` / `metric_set` / `function`

### Requirement: business_id 解析与头部回落

系统 SHALL 区分 `business_id`（HTTP `x-business-id` 头，租户上下文）；当头部参数为 null 时回落到 `huaweicloud.apm-business-id` 配置默认值；二者均缺失时返回 `INVALID_PARAM`，不发起上游调用。

#### Scenario: 头部 business_id 回落配置默认值
- **GIVEN** 调用未传 `business_id` 头部参数，但配置了 `huaweicloud.apm-business-id`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 用配置默认值注入 `x-business-id` 头

#### Scenario: 头部与配置默认值均缺失
- **GIVEN** 调用未传 `business_id`，且 `huaweicloud.apm-business-id` 未配置
- **WHEN** 调用工具
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: ShowMonitorItemViewConfig 无损投影

响应 DTO SHALL 无损覆盖 SDK `ShowMonitorItemViewConfigResponse` 的 `title` / `collectorName` / `viewRowList`（→ `ViewRow` → `ViewBase`，含 `fieldItemList` 的 `function` / `as`）。`ViewBase` 字段 SHALL 与 T22 `TrendView` 同构。任一字段缺失 MUST 导致契约测试失败。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `show-monitor-item-view-config-response.json`（env 1306682，JVM）
- **WHEN** 反序列化为 SDK 响应并经 adapter 映射为 DTO
- **THEN** `view_row_list` → `view_list` → 每个 view 的 `metric_set` / `view_type` 及 `field_item_list` 的 `function` / `as` SHALL 逐一断言通过
- **AND** 删除任一字段 SHALL 导致编译或断言失败

### Requirement: 发现结果 TTL 缓存

系统 SHALL 在 service 层用 Caffeine 对 `show_apm_monitor_item_view_config` 结果做 TTL 缓存（`expireAfterWrite=1d`），缓存 key 显式带 `env_id`（`#envId + '_' + #collectorId`）；上游异常或空结果 MUST 不写缓存。

#### Scenario: 同参第二次调用命中缓存
- **GIVEN** 已成功调用一次 `show_apm_monitor_item_view_config(collector_id=C, env_id=E)`
- **WHEN** 以相同 `collector_id=C` 与 `env_id=E` 再次调用
- **THEN** 系统 SHALL 命中缓存直接返回，不再调用 adapter（`verify(adapter, times(1))`）

#### Scenario: 缓存 key 带 env_id 防串号
- **GIVEN** 不同 env 下相同 `collector_id`
- **WHEN** 分别调用本工具
- **THEN** 系统 SHALL 因 key 含 `env_id` 而命中各自缓存条目，不串号

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

