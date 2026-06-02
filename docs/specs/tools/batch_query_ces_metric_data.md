# Spec: batch_query_ces_metric_data

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在一次调用中批量查询多条 CES 指标的数据点，避免对 `query_ces_metric_data` 的循环调用造成 N 次 HTTP + N 倍上游限流压力。

典型场景:
- 大盘渲染: Agent 同时拉取 ECS 集群 50 台机器的 `cpu_util` / `mem_util`
- 跨资源关联分析: Agent 在事故定位时一次拉取相关 ECS / RDS / EVS 指标做趋势对照
- 巡检任务: 一次查询多组 (namespace, metric, dimensions) 三元组

定位: 这是 `query_ces_metric_data` 的批量版替代物。
- 单条查询场景仍推荐 `query_ces_metric_data`（响应更小、错误隔离）
- 多条查询（≥ 2）一律推荐本 tool

## 2. 范围边界

**做**:
- 在一次请求里同时查询 1–500 个指标在指定时间区间、聚合粒度下的数据点
- 共享同一 {filter, period, from, to}（CES 上游约束）
- 每条指标各自带 {namespace, metric_name, dimensions}
- 输出按请求顺序对齐返回

**不做**:
- 不支持每条指标各自不同的 filter / period / 时间区间（受 CES 上游约束）
- 不支持单次超过 500 条（上游硬限制）
- 不做客户端聚合 / 重采样
- 不做跨 region / 跨 projectId

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `batch_query_ces_metric_data`
- description (Agent 看到的，决定它是否调用):

  > Batch query monitoring data points for multiple CES (Cloud Eye Service) metrics
  > in a single call. Up to 500 metric queries can be combined; each query specifies
  > namespace, metric_name and dimensions, and they all share the same filter /
  > period / from / to. Returns aggregated values (max / min / average / sum /
  > variance) per period bucket for every metric, in the same order as requested.
  > Prefer this over repeated query_ces_metric_data calls when fetching many
  > metrics for a dashboard or cross-resource analysis. Call list_ces_metrics first
  > to discover available metric names and dimensions. 'from'/'to' are UNIX
  > timestamps in milliseconds; 'period' is the aggregation granularity in seconds
  > (1 / 60 / 300 / 1200 / 3600 / 14400 / 86400).

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `metrics` | array<object> | 是 | — | 1–500 条查询项，结构见 §3.2.1 |
| `filter` | string | 是 | — | 聚合方式：`average` / `max` / `min` / `sum` / `variance`，对所有 metrics 生效 |
| `period` | int | 是 | — | 聚合粒度（秒）：1 / 60 / 300 / 1200 / 3600 / 14400 / 86400 |
| `from` | long | 是 | — | 起始时间，毫秒级 UNIX 时间戳 |
| `to` | long | 是 | — | 结束时间，毫秒级 UNIX 时间戳，**必须严格大于** `from` |

#### 3.2.1 `metrics[]` 元素

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `namespace` | string | 是 | CES namespace，如 `SYS.ECS`；格式 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$` |
| `metric_name` | string | 是 | 指标名，如 `cpu_util` |
| `dimensions` | array<{name, value}> | 是 | 1–4 个维度，每个 name/value 非空 |

**输入校验规则**:
- `filter` / `period` 未提供或不在枚举集 → `INVALID_PARAM`（Tool 层拦截）
- `from >= to` → `INVALID_PARAM`
- `metrics` 为空 / 超过 500 → `INVALID_PARAM`
- 任一 `metrics[i]` 的 namespace 格式不合规 → `INVALID_PARAM`
- 任一 `metrics[i].dimensions` 长度不在 [1,4] 或含空 name/value → `INVALID_PARAM`

### 3.3 输出契约（成功）

```json
{
  "metrics": [
    {
      "namespace": "SYS.ECS",
      "metric_name": "cpu_util",
      "dimensions": [
        {"name": "instance_id", "value": "d9112af5-6913-4f3b-bd0a-3f96711e004d"}
      ],
      "unit": "%",
      "datapoints": [
        {"timestamp": 1700000000000, "average": 23.5, "max": null, "min": null, "sum": null, "variance": null}
      ]
    }
  ]
}
```

字段说明:
- `metrics[]` 顺序与请求 `metrics[]` 顺序一致
- 单个 datapoint 的 `unit` 始终为 `null`（unit 在父级 `metrics[].unit` 上）
- 数据点列表按时间升序；CES 会按 period 向前取整 `from`，导致点数可能略多于预期（CES 上游行为）
- 未选择的聚合统计字段保持为 `null`

### 3.4 输出契约（失败）

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "human readable",
  "upstream_trace_id": "华为云返回的 X-Request-Id",
  "retryable": true | false
}
```

错误码映射:

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（Tool/Service 层） | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**: `batchListMetricData(BatchListMetricDataRequest)`
- **SDK 版本**: v3.1.177（实际部署版本，见 `CLAUDE.md` §1）
- **CES API 文档**: https://support.huaweicloud.com/intl/en-us/api-ces/

**字段映射**:

| MCP 输入 | SDK 字段 |
|---|---|
| `metrics[i].namespace` | `body.metrics[i].withNamespace(String)` |
| `metrics[i].metric_name` | `body.metrics[i].withMetricName(String)` |
| `metrics[i].dimensions[j]` | `body.metrics[i].dimensions[j] = new MetricsDimension().withName().withValue()` |
| `filter` | `body.withFilter(Filter.fromValue(...))` |
| `period` | `body.withPeriod(BatchListMetricDataRequestBody.PeriodEnum.fromValue("<seconds>"))` |
| `from` | `body.withFrom(Long)` |
| `to` | `body.withTo(Long)` |

**类型层面的强约束**（详见 ADR-004）:
- `filter` 在 DTO 中使用 `CesMetricFilter` 枚举（5 个值，严格）
- `period` 在 DTO 中使用 `CesMetricPeriod` 枚举（7 个值，严格）
- `namespace` / `metric_name` 仍以 `String` 承载，配合 `CesNamespace` / `CesMetric` 目录枚举作参考（宽容，允许新 SDK 值透传）

**AI 容易写错的点**（实现时务必注意）:
1. SDK 的 `BatchListMetricDataRequestBody.PeriodEnum.fromValue(...)` **接收字符串**（如 `"300"`），不是 `int`；要 `String.valueOf(period.getSeconds())`
2. `BatchListMetricData` 的 dimensions 是结构化对象（`MetricsDimension`），不是 `ShowMetricData` 的 `dim0` 字符串拼接
3. 响应里每个 `metrics[i]` 的 `unit` 在父级，单个 `DatapointForBatchMetric` 没有 unit 字段
4. SDK 响应类是 `BatchMetricData`（不是 `MetricInfoList`，不要和 `list_ces_metrics` 混淆）

## 5. 非功能要求

- **限流**: 复用 `ces-readonly` RateLimiter（与 `list_ces_metrics` / `query_ces_metric_data` 共享配额）
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标: `mcp_tool_invocation{tool="batch_query_ces_metric_data", result="...", error_code="..."}`
  - 日志 INFO: `metricCount` + `filter` + `period` + `from` + `to` + 耗时 + upstream trace_id

## 6. 测试策略（Definition of Done）

### 单元测试（mock service）

Tool 层（`CesBatchMetricDataToolTest`）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | filter/period 字符串成功解析为枚举 | service 收到 `CesMetricFilter.MAX` / `CesMetricPeriod.HOUR_1` |
| UT-02 | filter 为 null | INVALID_PARAM，不调 service，errorMessage 含 "filter" |
| UT-03 | period 为 null | INVALID_PARAM，不调 service，errorMessage 含 "period" |
| UT-04 | filter 未知（如 `p95`） | INVALID_PARAM，errorMessage 含取值 |
| UT-05 | period 未知（如 `7`） | INVALID_PARAM，errorMessage 含取值 |
| UT-06 | service 抛 InvalidParamException（如空 metrics） | 转 ErrorResponse，INVALID_PARAM，retryable=false |
| UT-07 | service 抛 UpstreamException(5xx) | 转 ErrorResponse，UPSTREAM_ERROR，retryable=true，含 trace id |

Service 层（建议放 `CesBatchMetricDataServiceTest`，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-S1 | metrics=null | INVALID_PARAM |
| UT-S2 | metrics 长度 0 | INVALID_PARAM |
| UT-S3 | metrics 长度 501 | INVALID_PARAM |
| UT-S4 | 任一 metric.namespace 格式非法 | INVALID_PARAM |
| UT-S5 | 任一 metric.dimensions 为空 | INVALID_PARAM |
| UT-S6 | 任一 metric.dimensions 长度 5 | INVALID_PARAM |
| UT-S7 | from >= to | INVALID_PARAM |
| UT-S8 | 全合法 | 委托 adapter 调用 |

Adapter 层（建议放 `CesMetricsAdapterImplTest`，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-A1 | 全合法请求 | SDK Request body 字段全部对齐（metrics、filter、period、from、to） |
| UT-A2 | 多 dimensions 映射 | SDK MetricInfo.dimensions 顺序与输入一致 |
| UT-A3 | SDK 返回空 metrics 列表 | DTO metrics=[] |
| UT-A4 | SDK 返回多条 metrics + datapoints | 字段全部透传，datapoint.unit=null |
| UT-A5 | SDK 抛 429 | 重试 3 次后 UPSTREAM_THROTTLED |
| UT-A6 | SDK 抛 401 | 不重试 UPSTREAM_AUTH_FAILED |

### 类型契约测试（建议补，本期未交付）

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `BatchListMetricDataRequestBody` 反射 | 包含 metrics / period / filter / from / to |
| TC-02 | SDK `BatchMetricData` 反射 | 包含 namespace / metricName / unit / dimensions / datapoints |
| TC-03 | SDK `DatapointForBatchMetric` 反射 | 包含 timestamp / max / min / average / sum / variance |
| TC-04 | 文档样例 JSON 反序列化 | 字段非 null |

### 部署后冒烟（贵阳环境，建议补，本期未交付）

`scripts/smoke/smoke-batch_query_ces_metric_data.sh`:

1. 2 条指标（同实例不同 metric），filter=average, period=300, 最近 1 小时 → 返回 `metrics.length == 2`，各自含 datapoints
2. metrics=[] → INVALID_PARAM
3. period=7 → INVALID_PARAM（Tool 层拦截，不打上游）

## 7. 验收标准（DoD）

- [x] Tool 层 UT-01~07 全部通过（7 条，见 `CesBatchMetricDataToolTest`）
- [ ] Service 层 UT-S1~8 全部通过（后续补 `CesBatchMetricDataServiceTest`）
- [ ] Adapter 层 UT-A1~6 全部通过（后续补 `CesMetricsAdapterImplTest` 新增方法）
- [ ] TC-01~04 类型契约测试全部通过（后续补）
- [x] MCP Inspector 能看到 `batch_query_ces_metric_data`，description 正确
- [x] 复用 `ces-readonly` RateLimiter
- [x] 日志含 metricCount / filter / period / from / to / 耗时 / upstream trace id
- [ ] Micrometer 指标 `mcp_tool_invocation` 可见
- [ ] 贵阳环境 3 条冒烟脚本通过
- [ ] README 含 tool 使用示例
- [x] Checkstyle 0 violations
