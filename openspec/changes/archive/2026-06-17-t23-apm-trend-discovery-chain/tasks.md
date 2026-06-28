> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T23-apm-trend-discovery-chain.md`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-apm）

- [x] 1.1 新增 `ApmDiscoveryAdapter` 接口 + `ApmDiscoveryAdapterImpl`（`showEnvMonitorItems` / `showMonitorItemViewConfig`，复用既有 `apmClient` 与 x-business-id 头注入）
- [x] 1.2 新增 DTO record：`ApmEnvMonitorItems`（`categoryInfoList` 4 字段 + `monitorItemInfoList` 10 字段无损投影，`collector_id` 为 Integer）
- [x] 1.3 新增 DTO record：`ApmViewConfig`（`title` / `collectorName` / `viewRowList` → `ViewRow` → `ViewBase`，含 `fieldItemList` 的 `function` / `as`，与 T22 `TrendView` 同构）
- [x] 1.4 SDK 异常 → `ErrorCode` 映射（429/401/5xx/Timeout），携带 `retryable` 与 `upstreamTraceId`

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `ApmDiscoveryService.getEnvMonitorItems(envId)`（薄：adapter 调用 + 异常转换 + business_id 解析回落）
- [x] 2.2 新增 `ApmDiscoveryService.getMonitorItemViewConfig(collectorId, envId)`（薄；注意 collectorId Integer↔Long 转递）
- [x] 2.3 `@Cacheable` Caffeine：env-items key `#envId`，view-config key `#envId + '_' + #collectorId`，`expireAfterWrite=1d`，`unless` 不缓存失败 / 空
- [x] 2.4 `CaffeineCacheManager` + `apm.discovery-cache.ttl`（默认 1d）+ `maximumSize`（默认 1000）配置；留 `@CacheEvict` 口子

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `ApmEnvMonitorItemsTool`，`@Tool(name="show_env_monitor_items")`，暴露 `env_id` + `business_id` 头部参数；描述写明发现链顺序与「collector_id 运行时解析、禁止硬编码」
- [x] 3.2 新增 `ApmViewConfigTool`，`@Tool(name="show_apm_monitor_item_view_config")`，暴露 `collector_id` + `env_id` + `business_id`；描述写明顺序与「禁止编造 collector_name/metric_set/function」
- [x] 3.3 改 `show_apm_trend`（T22）描述：补「show_env_monitor_items → show_apm_monitor_item_view_config → show_apm_trend」顺序与「view_config 取自 view_config 工具、禁止编造」红线；入参 / 响应不动
- [x] 3.4 `McpServerConfig` 注册两新工具（紧跟 `apmTrendTool`）

## 4. 测试

- [x] 4.1 `ApmEnvMonitorItemsToolTest` + `ApmViewConfigToolTest`（success passthrough / INVALID_PARAM / upstream 异常含 trace id）
- [x] 4.2 `ApmDiscoveryServiceTest`（business_id 回落 / 缓存命中 `verify(adapter, times(1))` / 失败不缓存 / collectorId 类型转递）
- [x] 4.3 `ApmDiscoveryAdapterImplTest`（字段全映射 + header 注入 + 4 异常映射）
- [x] 4.4 契约测试：`show-env-monitor-items-response.json`（本卡末实测）+ `show-monitor-item-view-config-response.json`（JVM 实测）→ 两 DTO 无损全字段断言
- [x] 4.5 保留 T22 trend 契约测试仍绿；`show_apm_trend` 无缓存验证

## 5. 文档

- [x] 5.1 修订 `CLAUDE.md §4.3`（发现真值转发版：入参禁止凭先验捏造上游结构，合法来源二选一，collector_id 运行时发现）

## 6. 遗留项（本期未交付）

- [ ] 6.1 `show_apm_monitor_item_detail` 工具（per-item interval，列 backlog，本期不做）
- [ ] 6.2 缓存手动失效的运维触发入口 / 配置开关（仅留 `@CacheEvict` 内部口子，未对外暴露）
- [ ] 6.3 多 env 部署下的缓存上 Redis 方案（单 env 单实例不做）
