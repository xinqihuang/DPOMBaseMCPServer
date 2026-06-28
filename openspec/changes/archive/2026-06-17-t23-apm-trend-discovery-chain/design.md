## Context

存量工具回填。原始任务卡：`docs/tasks/T23-apm-trend-discovery-chain.md`（状态 Done）；本卡取代 v2（viewKey 藏 function）与 v3（用 ShowMonitorItemDetail 逐项查），入口工具改为 `ShowEnvMonitorItems`。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射表、错误码→retryable、限流 / 重试 / 超时 / 可观测、各工具不一致的时间参数格式、缓存设计、AI 易错点。

本卡是「发现链」的两个发现工具（`show_env_monitor_items` / `show_apm_monitor_item_view_config`），与 T22 的查询工具 `show_apm_trend` 串联使用。三工具均只读，service 薄。

## Goals / Non-Goals

**Goals:**
- 无损暴露 `ShowEnvMonitorItems` 的 env 级监控项清单（含 monitor_item↔collector_id 映射）与 `ShowMonitorItemViewConfig` 的真实视图清单（含 `function` / `as`）。
- 让 Agent 自编排诊断链「`show_env_monitor_items` → `show_apm_monitor_item_view_config` → 选 view → `show_apm_trend`」，服务端不代编排。
- env 局部标识（`collector_id` 等）一律运行时发现，杜绝硬编码 / 跨 env 复用。
- 两发现工具加 TTL 缓存，缓存掉 MCP→华为云的 HTTP 往返，Agent 行为不变。

**Non-Goals:**
- 不做 `show_apm_monitor_item_view_config` 之外的视图融合 / 服务端代编排 / viewKey 藏 function / 静态目录（前版作废，若已生成则删）。
- 不做 `show_apm_monitor_item_detail`（per-item interval 列 backlog）。
- 不改 T22 `show_apm_trend` 的入参 / 响应 / 契约测试，仅改其描述文案。
- 不做客户端聚合 / 排序 / 曲线渲染；`show_apm_trend` 不缓存（趋势要实时）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 版本**：v3.1.x（钉死，字段缺失先怀疑版本）
- **复用既有 `apmClient` Bean**，不单独建 Bean；`x-business-id` 头空则回落 `huaweicloud.apm-business-id` 配置，与 T22 一致。

**工具 1 `show_env_monitor_items`**

- **SDK 方法**：`showEnvMonitorItems(ShowEnvMonitorItemsRequest{envId, xBusinessId})`
- **响应**：`ShowEnvMonitorItemsResponse{categoryInfoList, monitorItemInfoList}`

`CollectorCategoryInfo` → `ApmCollectorCategory`（无损，4 字段）：

| SDK | DTO | 类型 |
|---|---|---|
| category_id | categoryId | Integer |
| category_name | categoryName | String |
| display_name | displayName | String |
| sequence | sequence | Integer |

`MonitorItemEntity` → `ApmMonitorItem`（无损，10 字段）：

| SDK | DTO | 类型 |
|---|---|---|
| monitor_item_id | monitorItemId | Long |
| collector_id | collectorId | **Integer** |
| collector_name | collectorName | String |
| display_name | displayName | String |
| category_id | categoryId | Integer |
| disabled | disabled | Boolean |
| collect_interval | collectInterval | Integer |
| sequence | sequence | Integer |
| show_in_total | showInTotal | Boolean |

> 注：`MonitorItemEntity.collectorId` 是 **Integer**（不是 Long），透传给工具 2 时注意 Integer↔Long，勿丢精度勿猜类型。

**工具 2 `show_apm_monitor_item_view_config`**

- **SDK 方法**：`showMonitorItemViewConfig(ShowMonitorItemViewConfigRequest{collectorId, envId, xBusinessId})`
- **响应**：`ShowMonitorItemViewConfigResponse{title, collectorName, viewRowList, style}`

`ViewRow` → `ApmViewRow`：`{viewList:List<ApmViewBase>, title}`。
`ViewBase` → `ApmViewBase`（无损，与 T22 `TrendView` 字段同构）：

| SDK `ViewBase` | DTO | 类型 |
|---|---|---|
| collector_name | collectorName | String |
| metric_set | metricSet | String |
| title | title | String |
| view_type | viewType | String |
| table_direction | tableDirection | String |
| group_by | groupBy | String |
| filter | filter | String |
| field_item_list | fieldItemList | List<ApmFieldItem> |
| span | span | Boolean |
| span_field | spanField | String |
| order_by | orderBy | String |
| latest | latest | Boolean |

`FieldItem` → `ApmFieldItem`：`function` / `as` / `unit` / `visible` 等（与 T22 `FieldItem` 同构，全留）。

> `ViewBase`（工具 2 输出的单个 view）与 `TrendView`（工具 3 入参）字段同构：Agent 把选定的一个 `view_list[]` 条目原样填进工具 3 的 `view_config`。

### 时间参数格式（各工具不一致，显式标注）

- `show_env_monitor_items`：**无时间参数**（按 env 查清单，与时间窗无关）。
- `show_apm_monitor_item_view_config`：**无时间参数**（按 collector 查视图清单，与时间窗无关）。
- `show_apm_trend`（T22，本卡不改）：`start_time` / `end_time` 为 **String，epoch 毫秒（UTC ms）**，透传不转型；T22 描述称 ISO-8601，实际按 SDK 以 String 原样透传，不做本地解析。
- 对照其它工具的不一致格式（仅备查）：`list_apm_alarm_data` 时间为上游格式 String（未固定格式）；CES/AOM 系工具有用 `startMillis` / `endMillis` / `durationMinutes` 三元组者。本卡两工具均无时间参数，不涉及。

### 错误码→retryable

| 场景 | ErrorCode | retryable |
|---|---|---|
| 入参非法 / business_id 与默认均空 | INVALID_PARAM | false |
| 上游 429 | UPSTREAM_THROTTLED | true |
| 上游 401/403 | UPSTREAM_AUTH_FAILED | false |
| 上游 5xx | UPSTREAM_ERROR | true |
| 传输超时 | TIMEOUT | true |
| 其它 | INTERNAL | false |

每个失败响应携带 `retryable`(bool) 与 `upstreamTraceId`（华为云 `X-Request-Id`，可空）。SDK 异常不得透传到 MCP 层。

### 非功能（限流 / 重试 / 超时 / 可观测）

- **限流**：两发现工具复用 `apm-readonly` RateLimiter（10 QPS）。
- **重试**：复用 `huaweicloud-retryable`，仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避。
- **超时**：SDK 传输层超时 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="show_env_monitor_items"|"show_apm_monitor_item_view_config"}`；INFO 日志含 envId / collectorId / 结果条数 / 耗时 / upstreamTraceId；Logger 字段固定 `LOG`，SLF4J 占位符不拼接。

### 缓存设计（service 层 Caffeine，仅两发现工具）

- **缓存对象**：`show_env_monitor_items`、`show_apm_monitor_item_view_config` 结果；`show_apm_trend` **不缓存**。
- **缓存层**：service 层 `@Cacheable`（tool 层保持薄，不放缓存逻辑）。
- **实现**：Caffeine（本地堆内，单 env 单实例部署，不上 Redis），`CaffeineCacheManager`，`expireAfterWrite=1d`（配置项 `apm.discovery-cache.ttl=1d` 可调），`maximumSize`（默认 1000）防无界增长。
- **缓存 key（显式带 env_id）**：env-items 用 `#envId`；view-config 用 `#envId + '_' + #collectorId`。单 env 部署下 env 维度虽隐含，仍显式带上，杜绝将来多 env 复用时串号（即 collector_id env 局部性雷）。
- **手动失效**：留 `@CacheEvict` 口子，采集器变更时可立即清，不等 TTL。
- **不缓存失败**：上游异常 / 空结果不写缓存（`unless` 或 service 内判空），避免缓存穿透坏值。
- **TTL 取舍**：数据含 `disabled` 字段，监控项可停用 / 新增，TTL 1 天是「稳定」与「不长期用过期映射」的折中，不设永久。

### AI 易错点

1. **`collector_id` 禁止硬编码 / 跨 env 复用**——必经 `show_env_monitor_items` 运行时解析。这是本卡核心。
2. **三个独立工具，Agent 自编排**，别融合、别服务端代编排。
3. **T22 `show_apm_trend` 基本不动**，只改描述。
4. **不做 detail 工具、不藏 function、不做 viewKey / 静态目录**（前版作废）。
5. `collectorId` 类型：env-list 出 Integer，view-config 请求按 SDK（可能 Long），转递注意 Integer↔Long。
6. 入参以 SDK 源码为准，注意 `x-business-id` 头（空回落配置）。
7. Agent 须把工具 2 返回的某个 `view_list[]` 条目原样填进 `show_apm_trend.view_config`，禁止凭记忆编造 `collector_name` / `metric_set` / `function`。

## Risks / Trade-offs

- **缓存陈旧**：监控项停用 / 新增后，TTL 内可能返回过期映射；以 `@CacheEvict` 口子 + 1d TTL 折中，不设永久缓存。
- **collector_id 类型转递**：env-list 出 Integer，view-config 请求按 SDK，跨工具透传时若误转型会丢精度或选错采集器；契约测试钉死类型。
- **Agent 漏步 / 乱序**：三工具描述均写清调用顺序与「禁止编造」红线，降低 Agent 漏步风险；但仍依赖模型遵循，属软约束。
- **遗留**：MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
