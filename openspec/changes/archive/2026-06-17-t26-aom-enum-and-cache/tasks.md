> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T26-aom-enum-and-cache.md`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. 四个封闭集枚举（aom dto 包）

- [x] 1.1 新增 `AomMetricStatistic`（`maximum` / `minimum` / `sum` / `average` / `sampleCount`，5 值，含中文 Javadoc）
- [x] 1.2 新增 `AomMetricPeriod`（`60` / `300` / `900` / `3600`，4 值，`@JsonValue` int + `@JsonCreator fromSeconds(int)`，Schema 渲染整数枚举）
- [x] 1.3 新增 `AomFillValue`（`-1` / `0` / `null`(字面量字符串) / `average`，4 值）
- [x] 1.4 新增 `AomLogCategory`（`app_log` / `node_log` / `custom_log`，3 值）
- [x] 1.5 各枚举 `fromValue` 严格拒绝未知值（抛 `IllegalArgumentException`）；同时接受 API 字面量与枚举常量名两种写法；`.getValue()` / `.getSeconds()` 还原

## 2. 请求 DTO 枚举化 + adapter 还原 + service 删 Set

- [x] 2.1 `AomQueryMetricDataRequest`：`statistics` → `List<AomMetricStatistic>`、`period` → `AomMetricPeriod`、`fillValue` → `AomFillValue`
- [x] 2.2 `AomQueryLogsRequest`：`category` → `AomLogCategory`
- [x] 2.3 adapter SDK 映射处 `.getValue()` / `.getSeconds()` 还原字面量（`statistics`→`body.setStatistics(List<String>)`、`period`→`body.withPeriod(Integer)` 秒数直传、`fillValue`→`request.setFillValue(String)` 顶层、`category`→`body.withCategory(String)`）
- [x] 2.4 `AomMetricDataService` 删除 `ALLOWED_STATISTICS` / `ALLOWED_PERIODS` / `ALLOWED_FILL_VALUES`；`AomLogService` 删除 `ALLOWED_CATEGORIES`
- [x] 2.5 保留 null 必填校验、`MAX_DIMENSIONS`、分页范围等其余规则；`statistics` 空/null 保持可选语义

## 3. 工具入参枚举化 + 描述收紧（agentic-mcp）

- [x] 3.1 `query_aom_metric_data` 入参 `statistics` / `period` / `fillValue` 改枚举类型
- [x] 3.2 `query_logs` 入参 `category` 改枚举类型
- [x] 3.3 非法 statistics/period/fill_value/category 由框架（枚举反序列化失败）拒绝 → `INVALID_PARAM`
- [x] 3.4 `AomMetricsTool`（`list_aom_metrics`）描述标注 "Step 1 of the AOM query chain" + 缓存说明
- [x] 3.5 `AomMetricDataTool` 描述写明 metric_name / 维度名 MUST come from a prior `list_aom_metrics` response、禁止编造、调用顺序 `list_aom_metrics -> this tool`

## 4. 发现缓存（list_aom_metrics）

- [x] 4.1 `list_aom_metrics` 加 Caffeine 缓存 `aom-list-metrics`（复用 `DiscoveryCacheConfig`，与 CES 缓存独立），**TTL 默认 1h**，key = 整个 `AomListMetricsRequest` 请求 record
- [x] 4.2 失败 / 空结果不写缓存（`unless` 同时判 `null` 与 `isEmpty`）
- [x] 4.3 留 `@CacheEvict` 整体失效口
- [x] 4.4 `application.yml` 配置 `aom.discovery-cache.ttl` / `aom.discovery-cache.maximum-size`

## 5. 测试

- [x] 5.1 四个枚举契约测试：取值封闭性（含且仅含上表取值）+ 双写法解析 + 严格拒绝未知值
- [x] 5.2 Schema 可见性测试：`statistics`（含批量列表元素）/ `period` / `fill_value` / `category` 取值出现在工具 JSON Schema
- [x] 5.3 传非法 statistics/period/fill_value/category → 框架拒绝或 `INVALID_PARAM`
- [x] 5.4 service 层 `ALLOWED_*` Set 已删除（编译期 + 行为）
- [x] 5.5 缓存行为测试：同参命中（`verify(adapter, times(1))`）/ 空结果不缓存 / 异参不串 key / `@CacheEvict` 失效；TTL 默认 1h 且与 CES 缓存独立
- [x] 5.6 namespace 仍为 String、自定义 namespace 查询不受影响（既有 UT 保持绿）；响应 DTO 不动
- [x] 5.7 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量 `mvn verify` 一次通过；Checkstyle 0

## 6. 不做（本期未交付，遗留项）

- [ ] 6.1 namespace 枚举化（自定义命名空间合法，维持 String + `AomPatterns`，不做）
- [ ] 6.2 RDS 式双 namespace 处理（AOM 无 namespace 分裂问题，不做）
- [ ] 6.3 `inventory_id` 枚举化（`resType_resId` 复合字符串，收益低，不做）
- [ ] 6.4 改响应 DTO（§4.1 无损，不做）；`time_range` 格式与 `query_logs` keyword 语法（现有 Pattern 已覆盖，不做）
- [ ] 6.5 重建 `list_aom_metrics`（只加缓存 + 描述，不重建）
