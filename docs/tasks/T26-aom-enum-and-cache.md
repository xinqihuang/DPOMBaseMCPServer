# T26 — AOM 封闭集参数枚举化 + list_aom_metrics 缓存 + 描述收紧

> 状态: **Ready** · 估时: 0.5d · 依赖: T24（模式与基建：受控枚举进 Schema、DiscoveryCacheConfig）、ADR-004 · 关联: 无需改 §4.3（本卡是其 (a)+(b) 的 AOM 实例）

## 背景

T24 已对 CES 完成请求侧收口；AOM 三个工具（`list_aom_metrics` / `query_aom_metric_data` / `query_logs`）存在同类问题，但**分轴结论与 CES 不同**：

- **statistics / period / fill_value / category**：CES API 同款「封闭集且多年不变」→ **§4.3 (a) 严格枚举**（ADR-004 严格档）。现状是自由 String/Integer + service 层 `ALLOWED_*` Set 校验（`AomMetricDataService.java:35-41`、`AomLogService` 的 `ALLOWED_CATEGORIES`），取值对 Agent 不可见，只能靠描述文本。
- **namespace**：与 CES 的本质差异——AOM 除 5 个预定义值（`PAAS.CONTAINER/PAAS.NODE/PAAS.SLA/PAAS.AGGR/CUSTOMMETRICS`）外**合法接受用户自定义命名空间**（`AomPatterns.NAMESPACE` 与现有报错文案均明确容忍）。锁死枚举会废掉自定义指标查询 → **维持 String + 模式校验**（ADR-004 宽容目录档），本卡不动。
- **metric_name / 维度名**：按 namespace 变化、海量 → **§4.3 (b) 运行时发现**，`list_aom_metrics` 已存在，复用 + 加缓存。
- **无 RDS 式 fallback 需求**：AOM 不存在同一资源因部署形态分裂到两个 namespace 的问题，T24 的探测路由 / `resolved_namespace` 不适用本卡。

## 设计

**四个严格枚举（aom 模块 dto 包，参照 `CesMetricFilter` / `CesNamespace` 的写法）：**

```
AomMetricStatistic  MAXIMUM("maximum") / MINIMUM("minimum") / SUM("sum")
                    / AVERAGE("average") / SAMPLE_COUNT("sampleCount")
AomMetricPeriod     SEC_60(60) / SEC_300(300) / SEC_900(900) / SEC_3600(3600)   ← @JsonValue int
AomFillValue        MINUS_ONE("-1") / ZERO("0") / NULL_FILL("null") / AVERAGE("average")
AomLogCategory      APP_LOG("app_log") / NODE_LOG("node_log") / CUSTOM_LOG("custom_log")
```

- `fromValue` 沿用 T24 约定：**严格拒绝未知值**（抛 `IllegalArgumentException`），同时接受 API 字面量与枚举常量名两种写法（防 Schema 渲染形式与反序列化规则不一致）；`AomMetricPeriod` 以 `@JsonCreator fromSeconds(int)` 接受数字。
- **工具入参直接用枚举类型**（T24 namespace 同款，取值进 JSON Schema）：
  - `query_aom_metric_data`：`statistics` → `List<AomMetricStatistic>`、`period` → `AomMetricPeriod`、`fillValue` → `AomFillValue`
  - `query_logs`：`category` → `AomLogCategory`
- **请求 DTO 字段同步改枚举**（这四个是封闭集，走 ADR-004 严格档，DTO 直接持有 enum——与 namespace 的宽容档不同）；adapter 在 SDK 映射处 `.getValue()` / `.getSeconds()` 还原。
- service 层删除 `ALLOWED_PERIODS` / `ALLOWED_STATISTICS` / `ALLOWED_FILL_VALUES` / `ALLOWED_CATEGORIES`（被类型系统取代）；`null` 必填校验保留。

**缓存（复用 T24 的 `DiscoveryCacheConfig`，新增 cache）：**

- `list_aom_metrics` 加 Caffeine TTL 缓存：cache 名 `aom-list-metrics`，配置 `aom.discovery-cache.ttl` / `maximum-size`。
- **TTL 默认 1h（不照抄 CES 的 1d）**：AOM 指标清单含容器/进程级对象，churn 明显快于 CES 云服务目录，1d 会服务陈旧清单。
- key = 整个请求 record（`AomListMetricsRequest` 紧凑构造器已归一化 limit/start 默认值，值语义 equals 覆盖全部参数）；失败/空结果不写缓存；留 `@CacheEvict` 整体失效口。

**描述收紧（对齐 T24 措辞）：**

- `list_aom_metrics`：标注 "Step 1 of the AOM query chain"、缓存说明。
- `query_aom_metric_data`：metric_name / 维度名 **MUST come from a prior list_aom_metrics response — do NOT invent**；写明调用顺序 `list_aom_metrics -> this tool`。

## 范围

**做**：
1. 新增四个枚举（aom dto 包；严格 `fromValue` + 双写法兼容 + 中文 Javadoc）。
2. `AomQueryMetricDataRequest`（statistics/period/fillValue）与 `AomQueryLogsRequest`（category）字段改枚举；adapter SDK 映射处还原字面量；service 删除对应 `ALLOWED_*` Set。
3. 两个工具入参改枚举类型；`AomMetricDataTool` / `AomLogTool` / `AomMetricsTool` 描述收紧。
4. `list_aom_metrics` 加 Caffeine 缓存（`DiscoveryCacheConfig` 注册 `aom-list-metrics`，独立 spec，TTL 默认 1h）+ evict 口 + `application.yml` 配置项。
5. 测试：四个枚举的契约测试（取值封闭性 + 双写法 + 严格拒绝）、缓存行为测试（同参命中 / 空不缓存 / 异参不串 key / evict）、Schema 可见性测试（statistics/period/fill_value/category 取值出现在工具 JSON Schema）、既有 UT 适配。

**不做**：
- ❌ **namespace 不枚举**（自定义命名空间合法，维持 String + `AomPatterns` 校验——这是与 T24 的关键差异，别照搬）
- ❌ 不做 RDS 式 fallback / `resolved_namespace`（AOM 无 namespace 分裂问题）
- ❌ `inventory_id` 不枚举（`resType_resId` 复合字符串，枚举化收益低）
- ❌ 响应 DTO 一律不动（§4.1 无损）
- ❌ `time_range` 格式、`query_logs` 的 keyword 语法不动（格式约束非词表，现有 Pattern 校验已覆盖）
- ❌ 不重建 `list_aom_metrics`（只加缓存 + 描述）

## 验收标准

- [ ] 四个枚举各含且仅含上表取值；`fromValue` 接受字面量与常量名、未知值抛错
- [ ] 两工具入参为枚举；JSON Schema 中可见全部合法取值（含批量 statistics 列表元素）
- [ ] 传非法 statistics/period/fill_value/category → 框架拒绝或 `INVALID_PARAM`
- [ ] service 层 `ALLOWED_PERIODS/ALLOWED_STATISTICS/ALLOWED_FILL_VALUES/ALLOWED_CATEGORIES` 已删除
- [ ] `list_aom_metrics` 命中 Caffeine 缓存（同参二次调用 `verify(adapter, times(1))`）；空结果不缓存；TTL 配置默认 1h 且与 CES 缓存相互独立
- [ ] `query_aom_metric_data` 描述含「取自 list_aom_metrics、禁止编造、调用顺序」
- [ ] namespace 仍为 String，自定义 namespace 查询不受影响（既有 UT 保持绿）
- [ ] 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量 `mvn verify` 一次通过；Checkstyle 0

## AI 易错点提醒

1. **namespace 别枚举**——本卡与 T24 的核心差异，自定义命名空间是合法输入。
2. `sampleCount` 是驼峰、`fill_value` 的 `"null"` 是**字面量字符串**不是 Java `null`——枚举 value 照抄，别"修正"。
3. `AomMetricPeriod` 取值（60/300/900/3600）≠ CES 的 `CesMetricPeriod`（1/60/300/...），**不能复用 CES 枚举**，也别混进 ces 包。
4. period 走 `@JsonCreator fromSeconds(int)` 接受数字 JSON；Schema 渲染为整数枚举。
5. 缓存 key 用整个请求 record；`dimensions` 是 List，record equals 已覆盖，别自己拼字符串 key。
6. 失败/空结果不写缓存（`unless` 同时判 `null` 与 `isEmpty`）。
7. statistics 为空列表/`null` 时是可选参数，保持现状语义（SDK 侧默认），别强制必填。
8. 删 `ALLOWED_*` Set 时保留 null 必填校验与 `MAX_DIMENSIONS` 等其余规则。
9. 迭代只编单模块单测试（`-o -q -pl ... -am`），收尾再全量。
10. 与真实 SDK 冲突 → 停下来问（CLAUDE.md §5.1）。

## 完成后

PR：`refactor(T26): AOM closed-set enums + list_aom_metrics cache + discovery-chain descriptions`
