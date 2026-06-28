## Context

存量工具回填。原始 spec：`docs/specs/tools/query_aom_metric_data.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T08-query-aom-metric-data.md`（状态 Done，提交 `4c346d6`）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类/方法/版本、字段映射表、错误码→retryable 映射、AOM 特有的 `time_range` 字符串格式、非功能要求与 AI 易错点。

## Goals / Non-Goals

**Goals:**
- 无损暴露 AOM v2 `ListSample` 的单条时序取值能力，返回 per-period 数据点序列及各统计方式结果对。
- 与 AOM 诊断链前置 `list_aom_metrics`（发现 namespace/metric/dimensions）衔接，与 `query_ces_metric_data` 形成应用层 vs 基础设施层对称工具。
- 在 Service 层用"宽松目录"（`Set<>`）校验 period / statistics / fill_value，避免对仍在演进的 AOM 统计列表做强枚举锁定。

**Non-Goals:**
- 多条时序批量查询（AOM `ListSample` 上游支持 `samples[]` 多条，本 tool MVP 仅单条；留给后续 batch tool）。
- 客户端聚合 / 重采样 / 排序。
- 跨 projectId、跨 region。
- 日志查询（属 `query_logs`）。
- period / statistics 的强类型枚举（保留 Service 层 `Set<>` 校验）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.aom.v2.AomClient`
- **SDK 方法**：`listSample(ListSampleRequest)`
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本）
- **HTTP**：POST，body = `QuerySampleParam`

**请求字段映射（MCP 输入 → SDK）**：

| MCP 输入 | SDK 位置 | SDK 类型 |
|---|---|---|
| `namespace` | `body.samples[0].withNamespace(String)`（`QuerySample`） | String |
| `metric_name` | `body.samples[0].withMetricName(String)` | String |
| `dimensions[i]` | `body.samples[0].setDimensions(List<DimensionSeries>)` | `List<DimensionSeries>`（与 `list_aom_metrics` 用的 `Dimension` 非同一类） |
| `statistics` | `body.setStatistics(List<String>)` | `List<String>`（SDK 直接接受字符串列表） |
| `period` | `body.withPeriod(Integer)` | Integer（直接传秒数，与 CES 不同） |
| `time_range` | `body.withTimeRange(String)` | String（AOM 特有格式） |
| `fill_value` | `request.setFillValue(String)`（`ListSampleRequest` **顶层**，非 body） | String |

**响应字段映射（SDK → MCP 输出）**：

| MCP 输出 | SDK Response 字段 |
|---|---|
| `series[]` | `ListSampleResponse.getSamples()` → `List<SampleDataValue>` |
| `series[].namespace` / `metric_name` / `dimensions` | `SampleDataValue.getSample()` → `QuerySample`（响应里复用同一请求类） |
| `series[].datapoints[]` | `SampleDataValue.getDataPoints()` → `List<MetricDataPoints>` |
| `datapoints[].timestamp` / `unit` | `MetricDataPoints.getTimestamp()` / `.getUnit()` |
| `datapoints[].statistics[]` | `MetricDataPoints.getStatistics()` → `List<StatisticValue>`，映射为 `{statistic, value}` |

### 时间参数格式（AOM 特有）

- `time_range` **不是数字毫秒区间**，而是 AOM 特有字符串 `startMillis.endMillis.durationMinutes`，正则 `^(-1|\d{1,16})\.(-1|\d{1,16})\.\d{1,7}$`。
- `-1` 为占位符，由上游计算实际值（如 `-1.-1.60` = 最近 60 分钟）。
- 对照：CES `ShowMetricData` 用 from/to 的 UTC 毫秒长整型；CES `BatchListMetricData` 同理用毫秒；AOM 这里则是上述点分字符串。**写映射时不要凭印象套用 CES 的毫秒区间。**

### period 类型在三种 API 上的差异

- AOM `QuerySampleParam` 上是 **Integer 秒数直传**（`withPeriod(Integer)`）。
- CES `ShowMetricData` 上是 `PeriodEnum.fromValue(int)`。
- CES `BatchListMetricData` 上又是 `PeriodEnum.fromValue(String)`。
- 三种 API 三种类型——本 tool 走 AOM Integer 直传分支。

### Service 层校验目录

```
ALLOWED_PERIODS     = {60, 300, 900, 3600}
ALLOWED_STATISTICS  = {"maximum", "minimum", "sum", "average", "sampleCount"}
ALLOWED_FILL_VALUES = {"-1", "0", "null", "average"}
TIME_RANGE_PATTERN  = "^(-1|\\d{1,16})\\.(-1|\\d{1,16})\\.\\d{1,7}$"
NAMESPACE_PATTERN   = "^(PAAS\\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$"
MAX_DIMENSIONS      = 20
```

校验失败统一抛 `InvalidParamException` → `INVALID_PARAM`（`retryable=false`，不打上游）。period / statistics 不在 DTO/Tool 层做枚举，而在 Service 层用 `Set<>` 检查（与 T07 CES 严格枚举形成对照，ADR-004），因 AOM 业务方仍可能扩 statistic / period，不宜锁死。

### 错误码 → retryable 映射

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（Service 层） | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

失败响应携带 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

### 非功能要求

- **限流**：复用 `aom-readonly` RateLimiter（QPS=10，与 `list_aom_metrics` 共享配额）。
- **重试**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s。
- **超时**：单次 SDK 调用 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="query_aom_metric_data", result="...", error_code="..."}`；INFO 日志含 namespace / metricName / period / timeRange / 耗时 / upstream trace id。

### Tool 层职责

Tool 层只做 record 构造 + `SmartomException → ErrorResponse` 转换；不在 Tool 层做枚举解析（与 T07 不同——AOM 的 `statistics` 是 list，`period` 是宽松目录，不适合枚举）。

## Risks / Trade-offs

- **AOM 维度类双胞胎**：请求侧 `DimensionSeries`（在 `QuerySample.dimensions`）vs 响应侧 `Dimension`（在 `MetricItemResultAPI.dimensions`，list_aom_metrics 用），且与 CES 的 `MetricsDimension` 全不相同——adapter 不要复用 CES 转换函数。
- **statistics 请求/响应形状不同**：请求里是 `List<String>`，datapoint 响应里是 `List<StatisticValue>`（含 `statistic` + `value`）；DTO `AomMetricDatapoint.statistics` 是 list-of-pair，**不是平铺 max/min**（与 CES 设计相反）。
- **fillValue 层级易错**：`fillValue` 是 `ListSampleRequest` 顶层字段（`sdk.setFillValue`），不在 body（`body.setFillValue`）；SDK 两层级 setter 名相近，写完建议对照 OpenAPI 文档。
- **响应 namespace/metricName 来源**：`AomSampleSeries.namespace / metricName / dimensions` 来自 `SampleDataValue.getSample()`（复用请求侧 `QuerySample` 类），不要去读不存在的 `SampleDataValue.getNamespace`。
- **历史成功码**：AOM 响应里 `errorCode = SVCSTG_AMS_2000000` 是历史"成功"码，HTTP 200 时 adapter 不要当业务错误。
- **projectId 依赖**：AOM 调用需要 `projectId`（T06 已引入 `HUAWEICLOUD_PROJECT_ID`），缺失会启动失败；本任务不再重复加配置。
- **遗留**：MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
