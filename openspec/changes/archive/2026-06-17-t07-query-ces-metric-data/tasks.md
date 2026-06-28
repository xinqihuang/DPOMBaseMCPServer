> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T07`，状态 Done，实现 `4c346d6`，filter/period 枚举化重构 `ce6fd6c`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-ces）

- [x] 1.1 `CesMetricsAdapter` 接口新增 `queryMetricData` 方法（封装 SDK `ShowMetricData`）
- [x] 1.2 `CesMetricsAdapterImpl` 新增 `toShowMetricDataSdkRequest` / `toQueryMetricDataResponseDto` / `toDatapoint`，维度按序填入 `dim0`…`dim3` 的 `"name,value"` 字符串
- [x] 1.3 新增 DTO record：`CesQueryMetricDataRequest`（filter/period 经 T14 重构为枚举）/ `CesQueryMetricDataResponse` / `CesDatapoint`（平铺 max/min/average/sum/variance）
- [x] 1.4 SDK 异常 → `ErrorCode` 映射（429 / 401·403 / 5xx / Timeout）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `CesMetricDataService`，校验 namespace 正则 / dimensions 长度 1-4 / 维度 name·value 非空 / from < to / filter·period·metric_name 必填
- [x] 2.2 业务异常 `InvalidParamException` → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `CesMetricDataTool`，`@Tool(name="query_ces_metric_data")`，暴露 namespace / metric_name / dimensions / filter / period / from / to
- [x] 3.2 Tool 层 `parseFilter(String)` / `parsePeriod(Integer)` 解析为枚举，catch `IllegalArgumentException` 转 `InvalidParamException`（只取 message，判空避免 NPE）
- [x] 3.3 `McpServerConfig` 注册 `CesMetricDataTool`

## 4. 非功能

- [x] 4.1 复用 `ces-readonly` RateLimiter
- [x] 4.2 INFO 日志含 namespace / metricName / filter / period / from / to / 耗时 / upstream trace id
- [x] 4.3 Checkstyle 0 violations

## 5. 测试

- [x] 5.1 `CesMetricDataToolTest`（UT-01~07，7 条，T14 阶段补齐 Tool 层覆盖）

## 6. 遗留项（本期未交付）

- [ ] 6.1 批量查询 `batch_query_ces_metric_data`（拆到 T14）
- [ ] 6.2 Service 层 UT `CesMetricDataServiceTest`（UT-S1~S7）
- [ ] 6.3 Adapter 层 UT `CesMetricsAdapterImplTest` 新增方法（UT-A1~A6）
- [ ] 6.4 类型契约测试 TC-01~04
- [ ] 6.5 贵阳环境冒烟脚本 `scripts/smoke/smoke-query_ces_metric_data.sh`
- [ ] 6.6 Micrometer 指标看板配置
- [ ] 6.7 README 使用示例
