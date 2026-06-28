## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T26-aom-enum-and-cache.md`，状态 Done；PR `refactor(T26): AOM closed-set enums + list_aom_metrics cache + discovery-chain descriptions`），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

AOM 三个工具（`list_aom_metrics` / `query_aom_metric_data` / `query_logs`）的封闭集参数（`statistics` / `period` / `fill_value` / `category`）原以自由 `String` / `Integer` 入参承载，合法取值靠 service 层的 `ALLOWED_*` Set 校验，对 Agent 不可见、只能靠描述文本表达，大模型易拼错且无 Schema 约束。这四类取值是「封闭集且多年不变」（按 ADR-004 §4.3(a) 属「受控枚举」轴、严格档），适合在请求侧收敛为枚举，让合法取值经 Spring AI 反射直接出现在工具 JSON Schema 里。而 `metric_name` / 维度名按 namespace 变化且海量（§4.3(b)「运行时发现」轴），仍走已存在的 `list_aom_metrics` 发现链，不进枚举。

与 T24（CES）的关键差异：AOM 的 `namespace` 除 5 个预定义值外**合法接受用户自定义命名空间**，锁死枚举会废掉自定义指标查询，故本卡**不枚举 namespace**，维持 `String` + `AomPatterns` 模式校验（ADR-004 宽容目录档）。

本变更属**基础设施 / 横切（infra）**层：只收敛既有工具请求侧封闭集入参的类型承载方式、收紧工具描述里的发现链指引、为发现工具加缓存，**不引入任何对外的新工具能力，也不改任何 `@Tool(name=...)` 契约名与响应契约**。因此**不引入新的 capability spec、也不修改既有 capability spec**。

## What Changes

- 新增四个严格枚举（aom 模块 dto 包，参照 `CesMetricFilter` / `CesNamespace` 写法）：
  - `AomMetricStatistic`：`maximum` / `minimum` / `sum` / `average` / `sampleCount`（5 值）。
  - `AomMetricPeriod`：`60` / `300` / `900` / `3600`（4 值，`@JsonValue` int，`@JsonCreator fromSeconds(int)` 接受数字 JSON，Schema 渲染为整数枚举）。
  - `AomFillValue`：`-1` / `0` / `null`（字面量字符串）/ `average`（4 值）。
  - `AomLogCategory`：`app_log` / `node_log` / `custom_log`（3 值）。
  - 各枚举 `fromValue` 严格拒绝未知值（抛 `IllegalArgumentException`），同时接受 API 字面量与枚举常量名两种写法。
- 工具入参枚举化：`query_aom_metric_data` 的 `statistics` → `List<AomMetricStatistic>`、`period` → `AomMetricPeriod`、`fillValue` → `AomFillValue`；`query_logs` 的 `category` → `AomLogCategory`。
- 请求 DTO 字段同步改枚举（这四个走 ADR-004 严格档，DTO 直接持有 enum——与 namespace 宽容档不同）；adapter 在 SDK 映射处经 `.getValue()` / `.getSeconds()` 还原字面量。service 层删除 `ALLOWED_STATISTICS` / `ALLOWED_PERIODS` / `ALLOWED_FILL_VALUES` / `ALLOWED_CATEGORIES`（被类型系统取代），保留 `null` 必填校验与 `MAX_DIMENSIONS` 等其余规则。
- 工具描述收紧（对齐 T24 措辞）：`list_aom_metrics` 标注 "Step 1 of the AOM query chain" + 缓存说明；`query_aom_metric_data` 写明 metric_name / 维度名 MUST come from a prior `list_aom_metrics` response、禁止编造、调用顺序 `list_aom_metrics -> this tool`。
- `list_aom_metrics` 加 Caffeine TTL 缓存：cache 名 `aom-list-metrics`，**TTL 默认 1h**（不照抄 CES 的 1d，AOM 指标清单含容器/进程级对象 churn 更快），key = 整个请求 record，失败/空结果不写缓存，留 `@CacheEvict` 整体失效口；复用 T24 的 `DiscoveryCacheConfig`，与 CES 缓存相互独立。
- **约束（防蔓延）**：枚举只动请求侧，响应 DTO 一律不动（§4.1 无损）；`namespace` 不枚举（自定义命名空间合法，维持 String + `AomPatterns`）；不做 RDS 式双 namespace 处理（AOM 无 namespace 分裂问题）；`inventory_id` 不枚举；不动 `time_range` 格式与 `query_logs` 的 keyword 语法；不重建 `list_aom_metrics`（只加缓存 + 描述）。

## Capabilities

### New Capabilities

- 无（基础设施变更）。本变更不对外暴露任何新 MCP 工具能力，只收敛既有 AOM 工具请求侧封闭集入参的类型承载（String/Integer → 枚举）+ 工具描述收紧 + 发现工具缓存。

### Modified Capabilities

- 无。三个 AOM 工具的对外契约名与行为语义不变；封闭集入参从自由 String/Integer 收敛为受控枚举属请求侧输入校验收紧（非法值改由框架更早拒绝），响应契约保持不变，不构成 capability spec 的契约变更。

## Impact

- 模块（按依赖方向 `mcp → monitoring → adapter → common`）：
  - aom dto 包：新增 `AomMetricStatistic` / `AomMetricPeriod` / `AomFillValue` / `AomLogCategory` 四个枚举。
  - `agentic-mcp`：`AomMetricDataTool`（`statistics` / `period` / `fillValue` 入参改枚举）、`AomLogTool`（`category` 入参改枚举）描述收紧；`AomMetricsTool` 描述收紧（Step 1 + 缓存说明）。
  - `agentic-monitoring`：`AomMetricDataService` 删除 `ALLOWED_STATISTICS` / `ALLOWED_PERIODS` / `ALLOWED_FILL_VALUES`，`AomLogService` 删除 `ALLOWED_CATEGORIES`；保留 null 必填校验与 `MAX_DIMENSIONS` 等。
  - adapter：请求 DTO 字段改枚举（封闭集严格档），SDK 映射处 `.getValue()` / `.getSeconds()` 还原；响应 DTO 不动。
- 缓存：`list_aom_metrics` 链路新增 Caffeine 缓存 `aom-list-metrics`（TTL 默认 1h，key=请求 record，失败/空不缓存，`@CacheEvict` 整体失效口），复用 `DiscoveryCacheConfig`，与 CES 缓存独立。
- 配置：新增 `aom.discovery-cache.ttl` / `aom.discovery-cache.maximum-size` 等 `application.yml` 配置项；复用既有 AOM 限流域，不新增限流域。
- 兼容性：响应侧无任何变化（既有契约测试保持绿）；`namespace` 仍为 String，自定义 namespace 查询不受影响（既有 UT 保持绿）；唯一对外可见变化是工具 JSON Schema 中 `statistics` / `period` / `fill_value` / `category` 出现枚举取值约束、非法值更早被框架拒绝。
- 不涉及写操作；不改响应 DTO；不新增工具；不改 `@Tool(name=...)`；不枚举 namespace。
