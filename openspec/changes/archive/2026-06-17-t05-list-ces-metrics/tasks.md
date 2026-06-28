> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T05-list-ces-metrics.md`，依赖 T01-T04）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-ces）

- [x] 1.1 `CesMetricsAdapter` 接口新增 `listMetrics(CesListMetricsRequest)`
- [x] 1.2 `CesMetricsAdapterImpl` 实现：`toSdkRequest`（namespace / metricName / dim0 拼接 / limit / start / order）+ `toResponseDto`（metrics 投影 + MetaData → pagination + hasMore 派生）
- [x] 1.3 新增 DTO record：`CesListMetricsRequest`（紧凑构造规范化 limit/order）/ `CesListMetricsResponse` / `CesMetricInfo` / `CesMetricDimension` / `CesPagination`
- [x] 1.4 SDK 异常 → `ErrorCode` 映射（429/`RequestNotPermitted` / 401/403 / 5xx / Timeout），复用 `ces-readonly` 限流域与 `huaweicloud-retryable` 重试组

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `CesMetricsService.listMetrics`，校验 dim 成对 / limit∈[1,1000] / namespace 正则 / order 枚举
- [x] 2.2 业务异常 `InvalidParamException` → `INVALID_PARAM`，不发起上游调用

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `CesMetricsTool`，`@Tool(name="list_ces_metrics")`，暴露 7 个 `@ToolParam`（均 `required=false`）
- [x] 3.2 catch `SmartomException` 转 `ErrorResponse`（带 error_code / message / upstream_trace_id）
- [x] 3.3 `McpServerConfig` 注册 `CesMetricsTool` 到 `ToolCallbackProvider`

## 4. 测试

- [x] 4.1 `CesMetricsAdapterImplTest`（UT-01/02/03/09/10/11/12/13/14 + UT-15 unit 断言）
- [x] 4.2 `CesMetricsServiceTest`（UT-04/05/06/07/08 参数校验）
- [x] 4.3 `CesMetricsToolTest`（MCP 层 tool 调用）
- [x] 4.4 `CesListMetricsContractTest`（TC-01~TC-04，反射 + 样例 JSON）+ 样本 `sdk-samples/ces/list-metrics-response.json`

## 5. 遗留项（本期未交付）

- [ ] 5.1 query_metric_data / batch_query_metric_data 取数工具（后续任务）
- [ ] 5.2 AOM 对应 tool `list_aom_metrics`（后续任务）
- [ ] 5.3 录制回放 `mvn test -Precord` profile（预留，待跳板机条件）
