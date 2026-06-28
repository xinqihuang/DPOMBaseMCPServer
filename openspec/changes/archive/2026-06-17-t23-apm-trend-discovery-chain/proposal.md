## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T23-apm-trend-discovery-chain.md`，状态 Done；建立在 T22 `show_apm_trend` 之上）。此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

智能运维 Agent 在 APM 告警发生后要查指标趋势，必须先拿到 `monitor_item_id` 对应的 `collector_id` 以及该采集器的真实视图清单（`metric_set` / `function`），才能给 `show_apm_trend` 喂一个合法 `view_config`。而 `collector_id`、`collector_name`、`monitor_item_id` 都是 **env 内局部、会变** 的标识：实证 `env_id=1306682` 下 `collector_id=18` 是 JVM，而文档示例 env 下 `collector_id=18` 是 Exception——**同一 collector_id 跨 env 含义不同**，任何 collector_id↔采集器映射都禁止硬编码，必须每 env 运行时解析。

为此暴露两个薄的只读发现工具，让 Agent 自编排诊断链「`show_env_monitor_items` → `show_apm_monitor_item_view_config` → 选一个 view → `show_apm_trend`」，服务端不代编排、不藏 `function`、不建静态目录。

## What Changes

- 新增 MCP 只读工具 `show_env_monitor_items`：入 `env_id`（+ `business_id` 头部参数），出该 env 全部监控项与各自 `collector_id` / `collector_name` / `display_name` / `category`。这是发现入口与 monitor_item↔collector_id 的 env 级映射表。响应对 SDK `ShowEnvMonitorItemsResponse` 做**无损投影**（`categoryInfoList` 之 `CollectorCategoryInfo` 4 字段 + `monitorItemInfoList` 之 `MonitorItemEntity` 10 字段）。
- 新增 MCP 只读工具 `show_apm_monitor_item_view_config`：入 `collector_id` + `env_id`（+ `business_id` 头部参数），出该采集器真实视图清单（`title` / `metric_set` / `field_item_list` 含 `function` / `as`）。响应对 SDK `ShowMonitorItemViewConfigResponse` 做**无损投影**（`title` / `collectorName` / `viewRowList` → `ViewRow` → `ViewBase`，含 `fieldItemList`）。
- 两发现工具结果经 service 层 Caffeine TTL 缓存（`expireAfterWrite=1d`，缓存 key 显式带 `env_id`），命中即秒回，Agent 行为不变；上游异常/空结果不写缓存。
- `show_apm_trend`（T22）：入参 / 响应 / 契约测试**不动**，仅改 Tool 描述——写明调用顺序「`show_env_monitor_items` → `show_apm_monitor_item_view_config` → `show_apm_trend`」，并写明「`view_config` 取自 view_config 工具返回的某个 view，禁止自行编造 `collector_name` / `metric_set` / `function`」。
- 复用 `huaweicloud.apm-business-id` 配置默认值与 `apm-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `show-env-monitor-items`: 按 `env_id` 查出该环境全部监控项及各自 `collector_id` / `collector_name` / `display_name` / `category`，作为发现链入口与 monitor_item↔collector_id 的 env 级映射表，供 `show_apm_monitor_item_view_config` 下钻。
- `show-apm-monitor-item-view-config`: 按 `collector_id` + `env_id` 查出该采集器的真实视图清单（每个 view 的 `metric_set` / `field_item_list` 含 `function` / `as`），供 Agent 原样选取一个 view 喂给 `show_apm_trend`。

### Modified Capabilities

- `show-apm-trend`（T22，仅改描述，不改契约）: Tool 描述补充发现链调用顺序与「禁止编造 `collector_name` / `metric_set` / `function`」红线；入参、响应、契约测试均不变。

## Impact

- 模块：`agentic-adapter-apm`（新增 `ApmDiscoveryAdapter` + `ApmDiscoveryAdapterImpl` + DTO record：`ApmEnvMonitorItems` / `ApmViewConfig` 及其嵌套）、`agentic-monitoring`（新增 `ApmDiscoveryService` 的 `getEnvMonitorItems` / `getMonitorItemViewConfig`，带 `@Cacheable`）、`agentic-mcp`（新增 `ApmEnvMonitorItemsTool` / `ApmViewConfigTool` + `McpServerConfig` 注册；改 `show_apm_trend` 描述文案）。
- 配置：复用 `huaweicloud.apm-business-id`、`apm-readonly` RateLimiter；新增 `apm.discovery-cache.ttl`（默认 `1d`）与 Caffeine `maximumSize`（默认 1000）。
- 不涉及写操作；不改动 `show_apm_trend` 入参 / 响应 / 契约；不碰其它工具 / 分页 / 错误码。MCP 工具总数 +2。
