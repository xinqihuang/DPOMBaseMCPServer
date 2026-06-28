> 存量回填：以下任务已于早期 commit 交付（提交 `ce6fd6c`，原任务卡 `docs/tasks/T14`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. 枚举目录（agentic-adapter-ces，按 ADR-004）

- [x] 1.1 新增严格枚举 `CesMetricFilter`（average/max/min/sum/variance）+ `CesMetricPeriod`（1/60/300/1200/3600/14400/86400 秒），`@JsonValue` + `@JsonCreator` 配对，未知值抛 `IllegalArgumentException`
- [x] 1.2 新增宽容目录 `CesNamespace` / `CesDimensionKey` / `CesMetric`（ECS 基础监控 19 条），`fromValue` 未知值返回 null
- [x] 1.3 重构 `CesQueryMetricDataRequest` 的 filter/period 字段改为枚举类型

## 2. Adapter 层（agentic-adapter-ces）

- [x] 2.1 `CesMetricsAdapter` 新增 `batchQueryMetricData` 接口方法 + `CesMetricsAdapterImpl` 实现（枚举→SDK 映射，`PeriodEnum.fromValue(String.valueOf(seconds))`，dimensions 用结构化 `MetricsDimension`）
- [x] 2.2 新增 DTO record：`CesBatchMetricQuery` / `CesBatchQueryMetricDataRequest` / `CesBatchMetricResult` / `CesBatchQueryMetricDataResponse`（`BatchMetricData` 无损投影，unit 在父级，datapoint 不带 unit）
- [x] 2.3 SDK 异常 → `ErrorCode` 映射（429/401/5xx/Timeout）

## 3. Service 层（agentic-monitoring）

- [x] 3.1 新增 `CesBatchMetricDataService`：校验 `from < to` / `metrics` 长度 [1,500] / 每条 dimensions 长度 [1,4] / namespace 正则
- [x] 3.2 修改 `CesMetricDataService`：删除 `ALLOWED_FILTERS` / `ALLOWED_PERIODS` Set（类型系统强制）

## 4. MCP 工具层（agentic-mcp）

- [x] 4.1 新增 `CesBatchMetricDataTool`，`@Tool(name="batch_query_ces_metric_data")`，filter/period 字符串→枚举解析（catch `IllegalArgumentException` → `InvalidParamException`）
- [x] 4.2 修改 `CesMetricDataTool`：filter/period 改为字符串→枚举解析
- [x] 4.3 `McpServerConfig` 注册 `CesBatchMetricDataTool`

## 5. 测试

- [x] 5.1 `CesBatchMetricDataToolTest`（UT-01~07，7 条）
- [x] 5.2 `CesMetricDataToolTest`（7 条）

## 6. 非功能 / 验收

- [x] 6.1 复用 `ces-readonly` RateLimiter
- [x] 6.2 INFO 日志含 metricCount / filter / period / from / to / 耗时 / upstream trace id
- [x] 6.3 Checkstyle 0 violations，代码合入 master（`ce6fd6c`）

## 7. 遗留项（本期未交付）

- [ ] 7.1 Service 层 UT（`CesBatchMetricDataServiceTest`，UT-S1~8）
- [ ] 7.2 Adapter 层 UT（`CesMetricsAdapterImplTest` 新增方法，UT-A1~6）
- [ ] 7.3 类型契约测试（TC-01~04）
- [ ] 7.4 贵阳环境冒烟脚本 `scripts/smoke/smoke-batch_query_ces_metric_data.sh`（3 条）
- [ ] 7.5 Micrometer 指标 `mcp_tool_invocation` 看板
- [ ] 7.6 README 使用示例
