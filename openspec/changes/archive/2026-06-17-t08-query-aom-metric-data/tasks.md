> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T08-query-aom-metric-data.md`，状态 Done，提交 `4c346d6`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-aom）

- [x] 1.1 `AomMetricsAdapter` 新增 `queryMetricData` 能力（封装 `ListSample` SDK 调用，复用 T06 的 AOM adapter 基座与 projectId 注入）
- [x] 1.2 `AomMetricsAdapterImpl` 新增映射方法 `toListSampleSdkRequest` / `toQueryMetricDataResponseDto` / `toSampleSeries` / `toDatapoint`（请求侧用 `DimensionSeries`，响应侧从 `SampleDataValue.getSample()` 取 namespace/metricName/dimensions；`fillValue` 置于 `ListSampleRequest` 顶层）
- [x] 1.3 新增 DTO record：`AomQueryMetricDataRequest` / `AomQueryMetricDataResponse` / `AomSampleSeries` / `AomMetricDatapoint` / `AomStatisticValue`（datapoint statistics 为 list-of-pair）
- [x] 1.4 SDK 异常 → `ErrorCode` 映射（429 / 401-403 / 5xx / Timeout），历史成功码 `SVCSTG_AMS_2000000` 不当业务错误

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `AomMetricDataService`，校验 namespace 正则 / period ∈ {60,300,900,3600} / time_range 正则 / statistics 集合 / fill_value 集合 / dimensions ≤ 20
- [x] 2.2 校验失败统一抛 `InvalidParamException` → `INVALID_PARAM`，不打上游

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `AomMetricDataTool`，`@Tool(name="query_aom_metric_data")`，暴露 namespace / metric_name / dimensions / statistics / period / time_range / fill_value；Tool 层仅做 record 构造 + `SmartomException → ErrorResponse` 转换
- [x] 3.2 `McpServerConfig` 注册 `AomMetricDataTool`

## 4. 非功能

- [x] 4.1 复用 `aom-readonly` RateLimiter（QPS=10）
- [x] 4.2 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避重试，单次 SDK 超时 10s
- [x] 4.3 INFO 日志含 namespace / metricName / period / timeRange / 耗时 / upstream trace id
- [x] 4.4 Checkstyle 0 violations

## 5. 遗留项（本期未交付）

- [ ] 5.1 Tool 层 UT `AomMetricDataToolTest`（UT-01~03）
- [ ] 5.2 Service 层 UT `AomMetricDataServiceTest`（UT-S1~S9）
- [ ] 5.3 Adapter 层 UT `AomMetricsAdapterImplTest` 新增方法（UT-A1~A8）
- [ ] 5.4 类型契约测试 TC-01~06
- [ ] 5.5 贵阳环境冒烟脚本 `scripts/smoke/smoke-query_aom_metric_data.sh`
- [ ] 5.6 Micrometer 指标 `mcp_tool_invocation` 看板 + README 使用示例
