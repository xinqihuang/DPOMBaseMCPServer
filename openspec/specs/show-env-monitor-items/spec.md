# show-env-monitor-items Specification

## Purpose
APM 趋势发现链第 1 步：列出某 env 的监控项与采集器，是该 env 下 monitor_item↔collector_id 的权威映射；collector_id 是 env 局部标识，禁止硬编码或跨 env 复用。
## Requirements
### Requirement: 环境监控项发现查询

系统 SHALL 提供只读工具 `show_env_monitor_items`，调用 APM SDK `ShowEnvMonitorItems`，按 `env_id` 查出该环境全部监控项与采集器分类，返回 `{ category_info_list[], monitor_item_info_list[] }`，作为发现链入口与 monitor_item↔collector_id 的 env 级映射表。该工具 MUST 为只读，无时间参数，不做客户端聚合 / 排序 / 服务端代编排。

#### Scenario: 按 env_id 查监控项清单
- **WHEN** Agent 传入合法 `env_id`
- **THEN** 系统 SHALL 装配 `ShowEnvMonitorItemsRequest{envId, xBusinessId}` 调用上游
- **AND** 返回 `category_info_list[]` 与 `monitor_item_info_list[]`，每个监控项含 `monitor_item_id` / `collector_id` / `collector_name` / `display_name` / `category_id`

#### Scenario: 作为发现链第一步
- **GIVEN** Agent 持有某告警的 `monitor_item_id`，但不知其 `collector_id`
- **WHEN** 调用 `show_env_monitor_items(env_id)`
- **THEN** 系统 SHALL 返回该 env 下 monitor_item↔collector_id 的运行时映射
- **AND** Agent SHALL 据此取得 `collector_id` 后再调 `show_apm_monitor_item_view_config`，禁止凭先验硬编码或跨 env 复用 `collector_id`

### Requirement: business_id 解析与头部回落

系统 SHALL 区分 `business_id`（HTTP `x-business-id` 头，租户上下文）；当头部参数为 null 时，系统 SHALL 回落到 `huaweicloud.apm-business-id` 配置默认值；当二者均缺失时返回 `INVALID_PARAM`，不发起上游调用。

#### Scenario: 头部 business_id 回落配置默认值
- **GIVEN** 调用未传 `business_id` 头部参数，但配置了 `huaweicloud.apm-business-id`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 用配置默认值注入 `x-business-id` 头

#### Scenario: 头部与配置默认值均缺失
- **GIVEN** 调用未传 `business_id`，且 `huaweicloud.apm-business-id` 未配置
- **WHEN** 调用工具
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: ShowEnvMonitorItems 无损投影

响应 DTO SHALL 无损覆盖 SDK `ShowEnvMonitorItemsResponse` 的 `categoryInfoList`（`CollectorCategoryInfo` 全 4 字段）与 `monitorItemInfoList`（`MonitorItemEntity` 全 10 字段），类型对齐 SDK：`monitor_item_id` 为 `Long`，`collector_id` 为 `Integer`。任一字段缺失 MUST 导致契约测试失败。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `show-env-monitor-items-response.json`
- **WHEN** 反序列化为 SDK 响应并经 adapter 映射为 DTO
- **THEN** `category_info_list` 4 字段与 `monitor_item_info_list` 10 字段 SHALL 逐一断言通过
- **AND** `collector_id` SHALL 为 `Integer` 类型，删除任一字段 SHALL 导致编译或断言失败

### Requirement: 发现结果 TTL 缓存

系统 SHALL 在 service 层用 Caffeine 对 `show_env_monitor_items` 结果做 TTL 缓存（`expireAfterWrite=1d`，可由 `apm.discovery-cache.ttl` 配置），缓存 key 显式带 `env_id`（`#envId`）；上游异常或空结果 MUST 不写缓存。

#### Scenario: 同参第二次调用命中缓存
- **GIVEN** 已成功调用一次 `show_env_monitor_items(env_id=E)`
- **WHEN** 以相同 `env_id=E` 再次调用
- **THEN** 系统 SHALL 命中缓存直接返回，不再调用 adapter（`verify(adapter, times(1))`）

#### Scenario: 失败结果不写缓存
- **WHEN** 上游异常或返回空结果
- **THEN** 系统 SHALL 不写入缓存，下次同参调用 SHALL 重新打 adapter

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

