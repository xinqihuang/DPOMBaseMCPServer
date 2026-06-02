# T08 — 实现 query_aom_metric_data

> 状态: **Done**（提交 `4c346d6`，回溯补卡） · 估时: 1d · 依赖: T06（list_aom_metrics 落地的 AOM adapter 基座 + projectId 注入） · 关联 spec: `docs/specs/tools/query_aom_metric_data.md`

## 目标

按 `docs/specs/tools/query_aom_metric_data.md` 实现完整的 `query_aom_metric_data` MCP tool：让 Agent 拉取单条 AOM 时序在指定时间窗口、采样粒度下的数据点序列（含 maximum / minimum / sum / average / sampleCount 等多统计组合）。

## 范围

**做**:
- AOM adapter 新增 `queryMetricData` 能力（封装 `ListSample` SDK 调用）
- 自定义 DTO：`AomQueryMetricDataRequest` / `AomQueryMetricDataResponse` / `AomSampleSeries` / `AomMetricDatapoint` / `AomStatisticValue`
- 业务编排 `AomMetricDataService`（namespace 正则 / period 集合 / time_range 正则 / statistics 集合 / fill_value 集合 / dimensions 长度）
- MCP tool `AomMetricDataTool` 注册 + 错误码转换

**不做**（防止任务蔓延）:
- ❌ 多条时序批量查询（AOM `ListSample` 上游支持 `samples[]` 多条，留给后续 batch tool）
- ❌ Tool / Service / Adapter 层 UT、Contract Test、冒烟脚本（spec §6/§7 列出，本期未交付，进遗留项）
- ❌ filter / period / statistics 的强类型枚举（保留 Service 层 `Set<>` 校验；统计取值集合后续可能演进，先用宽松目录）
- ❌ CES 对应 tool（拆到 T07）

## 前置阅读

**必读**:
1. `docs/specs/tools/query_aom_metric_data.md` — 完整 spec
2. `docs/specs/tools/list_aom_metrics_v0.2.md` — AOM adapter 基础设施（projectId、namespace 枚举处理）
3. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一

**强烈推荐**:
4. AOM `ListSample` API：https://support.huaweicloud.com/intl/en-us/api-aom/
5. `docs/specs/tools/query_ces_metric_data.md` — 对称 tool，便于看清两者结构差异

## 产物清单

```
docs/specs/tools/
  query_aom_metric_data.md                            ← 本任务对应 spec（回溯补）
docs/tasks/
  T08-query-aom-metric-data.md                        ← 本任务卡（回溯补）

agentic-adapter/agentic-adapter-aom/
  src/main/java/com/huawei/smartom/agentic/adapter/aom/
    AomMetricsAdapter.java                            ← 加 queryMetricData 方法
    AomMetricsAdapterImpl.java                        ← 新增 toListSampleSdkRequest / toQueryMetricDataResponseDto / toSampleSeries / toDatapoint
    dto/
      AomQueryMetricDataRequest.java                  ← record
      AomQueryMetricDataResponse.java                 ← record
      AomSampleSeries.java                            ← record
      AomMetricDatapoint.java                         ← record
      AomStatisticValue.java                          ← record（statistic + value 对）

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/aom/
    AomMetricDataService.java                         ← 新增（namespace 正则 + period 集合 + time_range 正则 + statistics 集合 + fill_value 集合 + dimensions <=20）

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/AomMetricDataTool.java                       ← 新增 @Tool 注册
    config/McpServerConfig.java                       ← 注册 AomMetricDataTool
```

## 关键技术要求

### 1. DTO 设计

```java
public record AomQueryMetricDataRequest(
        String namespace,
        String metricName,
        List<AomMetricDimension> dimensions,
        List<String> statistics,
        Integer period,
        String timeRange,
        String fillValue) {
}

public record AomQueryMetricDataResponse(List<AomSampleSeries> series) {}

public record AomSampleSeries(
        String namespace,
        String metricName,
        List<AomMetricDimension> dimensions,
        List<AomMetricDatapoint> datapoints) {}

public record AomMetricDatapoint(
        Long timestamp,
        String unit,
        List<AomStatisticValue> statistics) {}

public record AomStatisticValue(String statistic, Double value) {}
```

注意 datapoint 的 `statistics` 是 list-of-pair，**不是平铺 max/min**（这点 AOM 和 CES 设计完全不同）。

### 2. Adapter 关键映射

```java
QuerySample sample = new QuerySample()
        .withNamespace(request.namespace())
        .withMetricName(request.metricName());
if (request.dimensions() != null && !request.dimensions().isEmpty()) {
    List<DimensionSeries> dims = request.dimensions().stream()
            .map(dim -> new DimensionSeries().withName(dim.name()).withValue(dim.value()))
            .toList();
    sample.setDimensions(dims);
}
QuerySampleParam body = new QuerySampleParam()
        .withSamples(List.of(sample))
        .withPeriod(request.period())            // Integer 秒数直传
        .withTimeRange(request.timeRange());
if (request.statistics() != null && !request.statistics().isEmpty()) {
    body.setStatistics(request.statistics());
}

ListSampleRequest sdk = new ListSampleRequest().withBody(body);
if (request.fillValue() != null) {
    sdk.setFillValue(request.fillValue());        // 顶层，不在 body
}
```

### 3. Service 层校验

```java
ALLOWED_PERIODS    = {60, 300, 900, 3600}
ALLOWED_STATISTICS = {"maximum", "minimum", "sum", "average", "sampleCount"}
ALLOWED_FILL_VALUES = {"-1", "0", "null", "average"}
TIME_RANGE_PATTERN = "^(-1|\\d{1,16})\\.(-1|\\d{1,16})\\.\\d{1,7}$"
NAMESPACE_PATTERN  = "^(PAAS\\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$"
MAX_DIMENSIONS     = 20
```

校验失败统一抛 `InvalidParamException`。

### 4. Tool 层

Tool 层只做 record 构造 + `SmartomException → ErrorResponse` 转换；不在 Tool 层做枚举解析（与 T07 不同——AOM 的 `statistics` 是 list，`period` 是宽松目录，不适合枚举）。

## 验收标准（实际完成项映射 spec §7）

- [x] 代码已合入 master（提交 `4c346d6`），含 Tool / Service / Adapter / DTO 全栈
- [x] MCP 配置文件注册 `AomMetricDataTool`
- [x] 复用 `aom-readonly` RateLimiter
- [x] 日志含 namespace / metricName / period / timeRange / 耗时 / upstream trace id
- [x] Checkstyle 0 violations
- [ ] Tool 层 UT（后续任务补 `AomMetricDataToolTest`）
- [ ] Service 层 UT（后续任务补 `AomMetricDataServiceTest`）
- [ ] Adapter 层 UT（后续任务补 `AomMetricsAdapterImplTest` 新增方法）
- [ ] 类型契约测试 TC-01~06（后续补）
- [ ] 贵阳冒烟脚本（后续补）
- [ ] Micrometer 指标 + README 示例（后续补）

## AI 易错点提醒

**spec §4 已列出**：
1. AOM 维度类有两个：请求侧 `DimensionSeries`（在 `QuerySample.dimensions`）、响应侧 `Dimension`（在 `MetricItemResultAPI.dimensions`，list_aom_metrics 用），**和 CES 的 `MetricsDimension` 全不相同**
2. `period` 在 AOM `QuerySampleParam` 上是 Integer 秒数直传；CES `ShowMetricData` 用 `PeriodEnum.fromValue(int)`；CES `BatchListMetricData` 用 `PeriodEnum.fromValue(String)`——三种 API 三种类型
3. `fillValue` 是 `ListSampleRequest` 的**顶层字段**，不在 body 里
4. `time_range` 不是数字毫秒区间，是 AOM 特有字符串 `startMs.endMs.durationMin`；`-1` 表示由上游计算
5. AOM 响应里 `errorCode = SVCSTG_AMS_2000000` 是历史"成功"码，adapter 不要当业务错误
6. `statistics` 在请求里是 `List<String>`，在 datapoint 响应里是 `List<StatisticValue>`（含 `statistic` + `value`）；形状不同
7. AOM 调用需要 `projectId`（T06 已引入 `HUAWEICLOUD_PROJECT_ID`），本任务不再重复加配置

**实施过程的额外踩坑点**：
8. `AomMetricDataTool` 没复用 T07 那种 `parseFilter/parsePeriod` 模式——AOM 的 `period` 校验放在 Service 层 `Set<Integer>` 里，与 ADR-004 提到的"CES 严格枚举"形成对照（AOM 业务方仍可能扩 statistic / period，不宜锁死）
9. `AomSampleSeries.namespace / metricName / dimensions` 来自 SDK 响应的 `SampleDataValue.getSample()`（**复用了请求侧 `QuerySample` 类**）——映射时不要直接读 `SampleDataValue.getNamespace`，那个字段不存在
10. `sdk.setFillValue` 与 `body.setFillValue` 容易写错位置，AOM SDK 这两个层级都有 setter 名相近的字段，写完 adapter 后建议人工对一遍 OpenAPI 文档

## 完成后

PR：`feat(aom): implement query_aom_metric_data tool`（已提交：`4c346d6`）。

PR 描述附上：
- 关联 spec
- 遗留项（Tool / Service / Adapter UT、Contract Test、冒烟脚本）
