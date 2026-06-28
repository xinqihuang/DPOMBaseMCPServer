> 存量回填：以下任务已于早期 commit 交付（源任务卡 `docs/tasks/T06-list-aom-metrics.md`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-aom）

- [x] 1.1 新增 `AomClientConfig`：用 `BasicCredentials().withProjectId().withAk().withSk()` + `AomRegion.valueOf(region)` 创建 `AomClient` bean，HTTP 超时 10s，启动日志不打 ak/sk
- [x] 1.2 新增 DTO record：`AomMetricDimension` / `AomMetricInfo`（含 `dimension_value_hash`）/ `AomPagination`（`next_token` 为 Integer）/ `AomListMetricsRequest`（compact constructor 设 limit=100、start=0 默认值）/ `AomListMetricsResponse`
- [x] 1.3 新增 `AomMetricsAdapter` 接口 + `AomMetricsAdapterImpl`：两条调用路径（A inventory_id / B namespace+dimensions），`limit`/`start` 用 `String.valueOf`，namespace 用 `NamespaceEnum.fromValue`，经 `HuaweiCloudInvocation.execute("aom-readonly","huaweicloud-retryable","aom.listMetricItems",...)`
- [x] 1.4 SDK 异常经 `SdkExceptionMapper` 映射（429/401/403/5xx/Timeout）；HTTP200 body 的 `SVCSTG_AMS_2000000` 成功码不当业务错误

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `AomMetricsService`，实现 §3.2 七条校验规则（namespace/inventory_id 至少其一、limit∈[1,1000]、start≥0、dimensions 非空、两个正则、inventory_id 优先记 WARN），短路求值
- [x] 2.2 校验失败抛 `InvalidParamException` → `INVALID_PARAM`，不调下游
- [x] 2.3 `agentic-monitoring` pom 加 aom adapter 依赖

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `AomMetricsTool`，`@Tool(name="list_aom_metrics")`，`@ToolParam` 描述与 spec §3.1 一致；`dimensions` 用 `List<AomMetricDimension>`
- [x] 3.2 `McpServerConfig` 的 `ToolCallbackProvider` 注册 `aomMetricsTool`；catch `SmartomException` 转 `ErrorResponse`

## 4. 配套基础设施（agentic-common / 配置）

- [x] 4.1 `HuaweiCloudProperties` 增加 `projectId` 字段 + getter/setter
- [x] 4.2 `HuaweiCloudCredentialsHealthIndicator` 增加 projectId 缺失检查（缺失只报 DOWN+detail 不阻断，javadoc 说明兼容 CES）
- [x] 4.3 `application.yml` 增加 `huaweicloud.project-id: ${HUAWEICLOUD_PROJECT_ID:}` 与 `resilience4j.ratelimiter.instances.aom-readonly`（QPS=10）

## 5. 测试

- [x] 5.1 `AomMetricsAdapterImplTest` 20 个 UT（UT-01~20，含 ArgumentCaptor 验证 String 转换、has_more 计算、dimension_value_hash 透传、异常映射）
- [x] 5.2 `AomMetricsServiceTest` 校验用例（UT-04~11，`verify(adapter, never())` 确认未调下游）
- [x] 5.3 `AomListMetricsContractTest` 5 个 TC（TC-01~05，含 AOM 独有 TC-04 校验 `NamespaceEnum` 嵌套类及静态常量）+ 样本 `sdk-samples/aom/list-metric-items-response.json`
- [x] 5.4 `AomClientConfigTest`（`@SpringBootTest` + `@TestPropertySource` 注入 ak/sk/region/project-id 验证 bean 创建）
- [x] 5.5 `AomMetricsToolTest`（错误转换 UT）

## 6. 冒烟脚本

- [x] 6.1 `scripts/smoke/smoke-list_aom_metrics.sh <host:port>` 3 个用例（namespace / inventory_id / limit=1001 → INVALID_PARAM；空 metrics 断言 count>=0）

## 7. 遗留项（本期未交付）

- [ ] 7.1 `query_aom_metric_data`（查指标值，T07）
- [ ] 7.2 跨 projectId / 跨 region 查询
- [ ] 7.3 缓存层
- [ ] 7.4 `docs/sdk-cheatsheet.md` 追加 AOM 字段映射速查表
