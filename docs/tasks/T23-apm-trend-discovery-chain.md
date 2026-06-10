# T23 — APM 趋势查询：三发现工具，Agent 自编排（env 级 collector 解析）

> 状态: **Done** · 估时: 1d · 依赖: **T22（show_apm_trend 保留）**、T19 §4.1 · 关联: 修订 `CLAUDE.md §4.3`
> 取代 v2（viewKey 藏 function）与 v3（用 ShowMonitorItemDetail 逐项查）。入口工具改为 **ShowEnvMonitorItems**。

## 思路（负责人定）

`collector_name`、`monitor_item_id`、**`collector_id` 都是 env 内局部、会变的**，不能静态绑定，也不要服务端替 Agent 编排。暴露三个薄工具，对应真实接口，Agent 自己按序调用：

1. **`show_env_monitor_items`** — 入 `env_id`，出该 env **全部监控项**及各自 `collector_id`/`collector_name`/`display_name`/`category`。这是发现入口与 **monitor_item↔collector_id 的 env 级映射表**。
2. **`show_apm_monitor_item_view_config`** — 入 `collector_id` + `env_id`，出该采集器**真实视图清单**（title / metric_set / field_item_list 含 function/as）。
3. **`show_apm_trend`** — Agent 从上一步挑一个 view 原样传入查趋势。（**= T22，保留**）

Agent 诊断流：告警给 `monitor_item_id` → 工具1 查出对应 `collector_id`（或按 display_name 选采集器）→ 工具2 拿视图清单 → 选定 view → 工具3 查趋势。

## 关键事实：collector_id 是 env 局部的（务必看）

实证：`env_id=1306682` 下 `collector_id=18` = **JVM**；而文档示例 env 下 `collector_id=18` = **Exception**、JVM 是 `collector_id=28`。**同一 collector_id 跨 env 含义不同。**

结论：**任何 collector_id↔采集器的映射都禁止硬编码**，必须每 env 经 `show_env_monitor_items` 运行时解析。这也是本设计放弃静态目录、放弃 ShowMonitorItemDetail 逐项查的根本原因。（答辩点：自描述、env 自适应。）

## SDK 能力（已核实）

- `showEnvMonitorItems(ShowEnvMonitorItemsRequest{envId, xBusinessId})` → `ShowEnvMonitorItemsResponse{categoryInfoList:List<CollectorCategoryInfo>{categoryId,categoryName,displayName,sequence}, monitorItemInfoList:List<MonitorItemEntity>{monitorItemId(Long), collectorId(Integer), collectorName, displayName, categoryId(Integer), disabled, collectInterval, sequence, showInTotal}}`
- `showMonitorItemViewConfig(ShowMonitorItemViewConfigRequest{collectorId, envId, xBusinessId})` → `ShowMonitorItemViewConfigResponse{title, collectorName, viewRowList:List<ViewRow{viewList:List<ViewBase>, title}>, style}`；`ViewBase{collectorName, metricSet, title, viewType, tableDirection, groupBy, filter, fieldItemList:List<FieldItem{function,as,unit,visible,...}>, span, spanField, orderBy, latest}`
- `showTrend(...)` → T22 已包

> `ViewBase`（工具2 输出的单个 view）与 `TrendView`（工具3 入参）字段同构，Agent 把选定的一个 `view_list[]` 条目原样填进工具3 的 `viewConfig`。

## 范围

**做**：
1. 新增 adapter + 无损 DTO（§4.1）：
   - `showEnvMonitorItems` → `ApmEnvMonitorItems`（无损：categoryInfoList + monitorItemInfoList 全字段）
   - `showMonitorItemViewConfig` → `ApmViewConfig`（无损：title/collectorName/viewRowList→ViewRow→ViewBase 全保留，含 fieldItemList 的 function/as）
2. 新增 service：`getEnvMonitorItems(envId)`、`getMonitorItemViewConfig(collectorId, envId)`（薄：adapter 调用 + 异常转换）。
3. 新增两个工具：`ApmEnvMonitorItemsTool#show_env_monitor_items`、`ApmViewConfigTool#show_apm_monitor_item_view_config`。
4. **`show_apm_trend`（T22）**：入参/响应不动；仅改描述——指引顺序「show_env_monitor_items → show_apm_monitor_item_view_config → show_apm_trend」，并写明「viewConfig 取自 view_config 工具返回的某个 view，**禁止自行编造** collector_name/metric_set/function」。
5. 契约测试：`show-env-monitor-items-response.json`（本卡末文档实测）+ `show-monitor-item-view-config-response.json`（JVM 实测）→ 断言两 DTO 无损；保留 T22 trend 契约测试。
6. **两发现工具加 TTL 缓存**（见「缓存设计」节）；`show_apm_trend` 不缓存。
7. 修订 `CLAUDE.md §4.3`（沿用 v3 的「发现真值转发」版，见下）。

**不做**：
- ❌ 不做 `show_apm_monitor_item_detail` 工具（collector_id 改由 env-list 提供；如需 per-item interval 再议，列 backlog）
- ❌ 不融合工具、不做 viewKey/藏 function/静态目录（前版过度设计，若已生成则删）
- ❌ 不改 T22 show_apm_trend 入参/响应/契约测试
- ❌ 不碰其它工具/分页/错误码

## §4.3 修订文本（替换现有 §4.3）

```markdown
### 4.3 面向 Agent 的工具入参：禁止凭先验捏造上游查询结构（重要）

§4.1 约束响应侧无损；本条约束请求侧。核心：Agent 不得用自身先验捏造上游的
collector_name/collector_id/metric_set/function/查询 DSL 等。合法来源二选一：
- (a) 受控枚举 / 带 allowed-values 的 key（服务端目录翻译）；或
- (b) 先调只读「发现工具」拿到上游真实结构，再原样转发给查询工具
  （show_env_monitor_items → show_apm_monitor_item_view_config → 选一个 view → show_apm_trend）。

判定红线：被透传的结构若可能由模型凭记忆生成，违规；若必然来自前置发现工具的真实响应，合规。
凡是 env 局部/会变的标识（如 collector_id），一律运行时发现，禁止硬编码或跨 env 复用。
查询工具描述必须写明：入参取自哪个发现工具、调用顺序、禁止自行编造。
```

## 缓存设计（MCP 服务端 TTL，仅两发现工具）

同一 env 下 `collector_id` 与 view-config 稳定，贵的是 MCP→华为云的 HTTP 往返。缓存掉这一跳，Agent 行为不变（照常调发现工具，命中即秒回）。**不涉及 OpenClaw 记忆**。

- **缓存对象**：`show_env_monitor_items`、`show_apm_monitor_item_view_config` 的结果。`show_apm_trend` **不缓存**（趋势要实时）。
- **缓存层**：service 层 `@Cacheable`（tool 层保持薄，不放缓存逻辑）。
- **实现**：**Caffeine**（本地堆内；单 env 单实例部署，不上 Redis，避免过度工程）。`CaffeineCacheManager`，**`expireAfterWrite = 1 天`**（配置项可调，如 `apm.discovery-cache.ttl=1d`）。建议设 `maximumSize`（如 1000）防无界增长。
- **缓存 key（显式带 env_id，务必）**：
  - env-items：`#envId`
  - view-config：`#envId + '_' + #collectorId`
  > 单 env 部署下 env 维度虽隐含，但 key 显式带上，杜绝将来多 env 复用时串号（即本卡反复强调的 collector_id env 局部性雷）。
- **手动失效**：留 evict 口子（`@CacheEvict` 内部方法或配置开关），采集器变更时可立即清，不必等 TTL。
- **不缓存失败**：上游异常/空结果不写缓存（`unless` 或 service 内判空），避免缓存穿透坏值。

> 注意：数据含 `disabled` 字段，监控项可停用/新增——TTL 1 天是「稳定」与「不长期用过期映射」的折中，不要设永久。

## 关键技术要求

- 三工具均只读；service 薄。
- **类型一致**：`MonitorItemEntity.collectorId` 是 **Integer**；`ShowMonitorItemViewConfigRequest.collectorId` 以 SDK 为准（可能 Long），转递时注意 Integer↔Long，**勿丢精度勿猜类型**。
- 请求字段以 SDK 源码为准（注意 `x-business-id` 头，空则回落配置，与 T22 一致）。
- trend 时间字段维持 T22：String、epoch ms、透传不转型。
- 工具描述写清调用顺序，降低 Agent 漏步/乱序。

## 验收标准

- [ ] `show_env_monitor_items(envId)` 无损返回 category + monitor_item 列表（每项含 collector_id/collector_name/display_name）
- [ ] `show_apm_monitor_item_view_config(collectorId, envId)` 无损返回视图清单（含 function/as）
- [ ] `show_apm_trend` 传入「从 view_config 选出的某 view」能拉趋势；入参/响应与 T22 一致
- [ ] 三工具描述含调用顺序 + 「禁止编造、collector_id 运行时解析」提示
- [ ] 两新 DTO 无损（契约测试覆盖，样本即本卡末两段 JSON）；T22 trend 契约测试仍绿
- [ ] 两发现工具结果经 Caffeine 缓存（`expireAfterWrite=1d`，key 带 env_id）；连续两次同参调用第二次不打 adapter（`verify(adapter, times(1))`）；`show_apm_trend` 无缓存
- [ ] `CLAUDE.md §4.3` 已替换
- [ ] 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量一次通过；Checkstyle 0；依赖方向不破

## AI 易错点提醒

1. **collector_id 禁止硬编码/跨 env 复用**——必经 show_env_monitor_items 运行时解析。这是本卡核心。
2. **三个独立工具，Agent 自编排**，别融合、别服务端代编排。
3. **T22 show_apm_trend 基本不动**，只改描述。
4. **不做 detail 工具、不藏 function、不做 viewKey/静态目录**（前版作废）。
5. `collectorId` 类型：env-list 出 Integer，view-config 请求按 SDK；注意转递类型。
6. 入参以 SDK 源码为准，注意 `x-business-id`；trend 时间 String 透传。
7. **迭代只编单模块单测试**（`-o -q -pl ... -am`），收尾再全量。
8. 任务卡与真实 SDK 冲突 → 停下来问（CLAUDE.md §5.1）。

## 完成后

PR：`feat(T23): APM env-level monitor-item discovery + view_config tools feeding show_apm_trend`

---

## 附 1：`ShowEnvMonitorItems` 实测响应 → sdk-samples（show-env-monitor-items-response.json）

```json
{
  "category_info_list": [
    {"category_id": 7, "category_name": "Url", "display_name": "接口调用", "sequence": 1},
    {"category_id": 5, "category_name": "Base", "display_name": "基础监控", "sequence": 20},
    {"category_id": 4, "category_name": "Exception", "display_name": "异常", "sequence": 30},
    {"category_id": 11, "category_name": "Web", "display_name": "Web容器", "sequence": 80},
    {"category_id": 10, "category_name": "ProbeInfo", "display_name": "探针监控", "sequence": 90}
  ],
  "monitor_item_info_list": [
    {"monitor_item_id": 37, "disabled": false, "collector_id": 50, "sequence": 1,  "collect_interval": 60, "category_id": 7,  "collector_name": "Url",       "display_name": "URL监控",  "show_in_total": true},
    {"monitor_item_id": 16, "disabled": false, "collector_id": 36, "sequence": 5,  "collect_interval": 60, "category_id": 5,  "collector_name": "JVMInfo",   "display_name": "JVM信息",  "show_in_total": true},
    {"monitor_item_id": 14, "disabled": false, "collector_id": 28, "sequence": 10, "collect_interval": 60, "category_id": 5,  "collector_name": "JVM",       "display_name": "JVM监控",  "show_in_total": true},
    {"monitor_item_id": 18, "disabled": false, "collector_id": 38, "sequence": 10, "collect_interval": 60, "category_id": 5,  "collector_name": "GC",        "display_name": "GC监控",   "show_in_total": true},
    {"monitor_item_id": 20, "disabled": false, "collector_id": 48, "sequence": 10, "collect_interval": 60, "category_id": 5,  "collector_name": "Thread",    "display_name": "线程",     "show_in_total": true},
    {"monitor_item_id": 13, "disabled": false, "collector_id": 20, "sequence": 15, "collect_interval": 60, "category_id": 5,  "collector_name": "JavaMethod","display_name": "JAVA方法", "show_in_total": true},
    {"monitor_item_id": 12, "disabled": false, "collector_id": 18, "sequence": 20, "collect_interval": 60, "category_id": 4,  "collector_name": "Exception", "display_name": "异常日志", "show_in_total": true},
    {"monitor_item_id": 41, "disabled": false, "collector_id": 24, "sequence": 55, "collect_interval": 60, "category_id": 11, "collector_name": "Tomcat",    "display_name": "Tomcat监控","show_in_total": true},
    {"monitor_item_id": 11, "disabled": false, "collector_id": 16, "sequence": 60, "collect_interval": 60, "category_id": 10, "collector_name": "ProbeInfo", "display_name": "探针监控", "show_in_total": true}
  ]
}
```

## 附 2：`get-monitor-item-view-config?collector_id=18`（env 1306682，JVM）→ sdk-samples（show-monitor-item-view-config-response.json）

```json
{
  "title": "JVM", "collector_name": "JVM",
  "view_row_list": [
    { "view_list": [
      { "collector_name": "JVM", "metric_set": "thread", "title": "线程", "group_by": "", "filter": "", "view_type": "trend",
        "field_item_list": [
          {"function": "MAX(threadCount)", "as": "当前线程数", "visible": true},
          {"function": "MAX(totalStartedThreadCount)", "as": "所有启动线程数", "visible": true},
          {"function": "MAX(peakThreadCount)", "as": "峰值线程数", "visible": true},
          {"function": "MAX(deadlockedThreadsCount)", "as": "死锁线程数", "visible": true},
          {"function": "MAX(daemonThreadCount)", "as": "守护线程数", "visible": true} ] },
      { "collector_name": "JVM", "metric_set": "thread", "title": "线程状态", "group_by": "", "filter": "", "view_type": "trend",
        "field_item_list": [
          {"function": "MAX(newThreadCount)", "as": "NEW状态线程数", "visible": true},
          {"function": "MAX(runnableThreadCount)", "as": "RUNNABLE状态线程数", "visible": true},
          {"function": "MAX(blockedThreadCount)", "as": "BLOCKED状态线程数", "visible": true},
          {"function": "MAX(waitingThreadCount)", "as": "WAITING状态线程数", "visible": true},
          {"function": "MAX(timedWaitingThreadCount)", "as": "TIMED_WAITING状态线程数", "visible": true},
          {"function": "MAX(terminatedThreadCount)", "as": "TERMINATED状态线程数", "visible": true} ] }
    ], "title": "" },
    { "view_list": [
      { "collector_name": "JVM", "metric_set": "memory", "title": "内存", "group_by": "", "filter": "", "view_type": "trend",
        "field_item_list": [
          {"function": "AVG(heapMemoryUsage)", "as": "堆内存使用(MB)", "visible": true},
          {"function": "AVG(nonHeapMemoryUsage)", "as": "非堆内存使用(MB)", "visible": true},
          {"function": "AVG(directMemoryUsage)", "as": "直接内存使用(MB)", "visible": true},
          {"function": "AVG(heapMemoryMax)", "as": "最大堆内存(MB)", "visible": true} ] },
      { "collector_name": "JVM", "metric_set": "classLoading", "title": "类加载", "group_by": "", "filter": "", "view_type": "trend",
        "field_item_list": [
          {"function": "AVG(loadedClassCount)", "as": "当前类个数", "visible": true},
          {"function": "AVG(totalLoadedClassCount)", "as": "总共加载类个数", "visible": true},
          {"function": "AVG(unloadedClassCount)", "as": "卸载的类个数", "visible": true} ] }
    ], "title": "" },
    { "view_list": [
      { "collector_name": "JVM", "metric_set": "memoryUsageRatio", "title": "内存使用率%", "filter": "", "view_type": "trend",
        "field_item_list": [ {"function": "AVG(heapMemoryUsageRatio)", "as": "堆内存使用率%", "visible": true} ] },
      { "collector_name": "JVM", "metric_set": "cpu", "title": "cpu(%)", "filter": "", "view_type": "trend",
        "field_item_list": [ {"function": "AVG(cpuRatio)", "as": "使用率%", "visible": true} ] }
    ], "title": "" },
    { "view_list": [
      { "latest": true, "collector_name": "JVM", "metric_set": "memoryPool", "title": "内存池", "table_direction": "H",
        "group_by": "name", "filter": "", "order_by": "", "view_type": "sumtable",
        "field_item_list": [
          {"function": "AVG(max)", "as": "max(MB)", "visible": true},
          {"function": "AVG(used)", "as": "used(MB)", "visible": true},
          {"function": "AVG(init)", "as": "init(MB)", "visible": true},
          {"function": "AVG(committed)", "as": "committed(MB)", "visible": true} ] }
    ], "title": "" }
  ]
}
```
