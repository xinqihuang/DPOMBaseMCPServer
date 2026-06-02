# Spec: query_aom_metric_data

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 拉取 **单条 AOM 时序** 在指定时间窗口、采样粒度下的数据点序列，得到应用/组件/容器/进程/节点等业务层监控数值。

典型场景:
- Agent 收到应用级告警（appName=order-svc CPU 飙高）→ 拉过去 1 小时 `cpuUsage` 的 `maximum` / `average` 曲线
- Agent 做容量分析：取节点过去 24 小时 `aom_node_memory_usage` 的 `average`
- Agent 关联事件：基于 `list_aom_metrics` 找到 `dimension_value_hash` 后，按同一组维度多次取值对比

定位:
- 前置：`list_aom_metrics`（T06）用来发现合法的 namespace + metric_name + dimensions
- 对称：与 `query_ces_metric_data` 是应用层 vs 基础设施层的两套独立 tool（SDK 不同包、字段不同结构、period/filter 取值集不同）

## 2. 范围边界

**做**:
- 调 AOM v2 `ListSample` API，按 `(namespace, metricName, dimensions)` 单条时序拉数据点
- 支持 `maximum` / `minimum` / `sum` / `average` / `sampleCount` 5 种统计组合（一次可选多个）
- 支持 `period` 60 / 300 / 900 / 3600 秒
- 支持 AOM 风格的 `timeRange` 字符串（`startMs.endMs.durationMin`）
- 支持断点插值策略 `fillValue`（`-1` / `0` / `null` / `average`）

**不做**:
- 不一次查多条时序（AOM `ListSample` 支持 `samples[]` 多条，但本 tool MVP 仅暴露单条；多条留给后续 batch tool）
- 不做客户端聚合 / 重采样
- 不做跨 projectId、跨 region
- 不做日志查询（那是 `query_logs`）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `query_aom_metric_data`
- description（Agent 看到的）:

  > Query AOM (Application Operations Management) sample series data over a time
  > window for a Huawei Cloud application/container/process/node metric. Returns
  > per-period datapoints with selected aggregations (maximum / minimum / sum /
  > average / sampleCount). Call list_aom_metrics first to discover available
  > metric names and dimensions. 'period' is the sampling granularity in seconds
  > (60 / 300 / 900 / 3600). 'time_range' format is
  > 'startMillis.endMillis.durationMinutes' — use -1 for either start or end to
  > let the server compute it (e.g. '-1.-1.60' = last 60 min).

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `namespace` | string | 是 | — | AOM namespace，例如 `PAAS.CONTAINER` / `PAAS.NODE` / `PAAS.SLA` / `PAAS.AGGR` / `CUSTOMMETRICS`，或自定义命名空间 |
| `metric_name` | string | 是 | — | 时间序列名，例如 `cpuUsage` |
| `dimensions` | array<{name, value}> | 否 | null | 维度过滤列表，0–20 个；每个 name/value 必须非空 |
| `statistics` | array<string> | 否 | null | 统计方式列表，可选值 `maximum` / `minimum` / `sum` / `average` / `sampleCount`；为空时由上游决定默认 |
| `period` | int | 是 | — | 采样粒度（秒），取值 `60` / `300` / `900` / `3600` |
| `time_range` | string | 是 | — | 时间窗口字符串，正则 `^(-1\|\d{1,16})\.(-1\|\d{1,16})\.\d{1,7}$`，例如 `-1.-1.60` = 最近 60 分钟 |
| `fill_value` | string | 否 | null | 断点插值策略，取值 `-1` / `0` / `null` / `average` |

**输入校验规则**（在 Service 层做，违反抛 `InvalidParamException`）:
- `namespace` 缺失或不匹配正则 `^(PAAS\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$` → `INVALID_PARAM`
- `metric_name` 缺失 → `INVALID_PARAM`
- `period` 缺失或不在 {60, 300, 900, 3600} → `INVALID_PARAM`
- `time_range` 缺失或不匹配上述正则 → `INVALID_PARAM`
- `statistics` 任一元素不在 {maximum, minimum, sum, average, sampleCount} → `INVALID_PARAM`
- `fill_value` 提供但不在 {-1, 0, null, average} → `INVALID_PARAM`
- `dimensions` 长度 > 20 或任一 name/value 为空 → `INVALID_PARAM`

> 注：与 CES 不同，AOM 的 `period` 不在 DTO 层做枚举，而是 Service 层用 `Set<Integer>` 检查；`statistics` 同理用 `Set<String>` 检查。本期保留这种"宽松目录"写法，避免对 AOM 业务方仍在演进的 statistic 列表做强锁。

### 3.3 输出契约（成功）

```json
{
  "series": [
    {
      "namespace": "PAAS.CONTAINER",
      "metric_name": "cpuUsage",
      "dimensions": [
        {"name": "appName", "value": "order-svc"}
      ],
      "datapoints": [
        {
          "timestamp": 1700000000000,
          "unit": "Percent",
          "statistics": [
            {"statistic": "maximum", "value": 87.3},
            {"statistic": "average", "value": 42.1}
          ]
        }
      ]
    }
  ]
}
```

字段说明:
- `series[]` 来自 SDK `ListSampleResponse.getSamples()`；当前 tool 仅传入单条 `QuerySample`，**正常应只返回 1 条 series**，但 SDK 返回结构是 List，本 tool 不做强约束
- `series[].dimensions` 来自上游响应回填，与请求维度可能存在顺序差异
- `series[].datapoints[].statistics[]` 是每个时间点上各统计方式的结果对——AOM 的设计与 CES 平铺 `max / min / average` 字段不同，**这里是 list of pairs**
- `datapoints[].unit` 在 datapoint 级别返回，单位例如 `Percent`

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
| 输入校验失败（Service 层） | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.aom.v2.AomClient`
- **SDK 方法**: `listSample(ListSampleRequest)`
- **SDK 版本**: v3.1.177
- **HTTP 方法**: POST，body 是 `QuerySampleParam`

**字段映射**:

| MCP 输入 | SDK 位置 | SDK 类型 |
|---|---|---|
| `namespace` | `body.samples[0].withNamespace(String)`（`QuerySample`） | String |
| `metric_name` | `body.samples[0].withMetricName(String)` | String |
| `dimensions[i]` | `body.samples[0].setDimensions(List<DimensionSeries>)` | `List<DimensionSeries>`（**与 `list_aom_metrics` 用的 `Dimension` 不是同一个类**） |
| `statistics` | `body.setStatistics(List<String>)` | `List<String>`（SDK 直接接受字符串列表） |
| `period` | `body.withPeriod(Integer)` | Integer（**直接传秒数，与 CES 不同**） |
| `time_range` | `body.withTimeRange(String)` | String（AOM 特有格式） |
| `fill_value` | `request.setFillValue(String)`（位于 `ListSampleRequest` 顶层，非 body） | String |

**Response 字段映射**:

| MCP 输出 | SDK Response 字段 |
|---|---|
| `series[]` | `ListSampleResponse.getSamples()` → `List<SampleDataValue>` |
| `series[].namespace` / `metric_name` / `dimensions` | `SampleDataValue.getSample()` → `QuerySample`（在响应里也复用同一类） |
| `series[].datapoints[]` | `SampleDataValue.getDataPoints()` → `List<MetricDataPoints>` |
| `datapoints[].timestamp` / `unit` | `MetricDataPoints.getTimestamp()` / `.getUnit()` |
| `datapoints[].statistics[]` | `MetricDataPoints.getStatistics()` → `List<StatisticValue>`，映射为本 spec 的 `{statistic, value}` |

**AI 容易写错的点**（实现时务必注意）:
1. AOM 的维度类是 `DimensionSeries`（请求侧 `QuerySample.dimensions`）和 `Dimension`（响应侧 `MetricItemResultAPI.dimensions`），**和 CES 的 `MetricsDimension` 完全不同**——adapter 不要复用 CES 的转换函数
2. `period` 在 AOM `QuerySampleParam` 上是 **Integer 秒数直传**；在 CES `ShowMetricData` 上是 `PeriodEnum.fromValue(int)`；在 CES `BatchListMetricData` 上又是 `PeriodEnum.fromValue(String)`——三种 API 三种类型，写映射时不要凭印象
3. `fillValue` 是 `ListSampleRequest` 的顶层字段，**不在 body 里**——`sdk.setFillValue(...)` 而不是 `body.setFillValue(...)`
4. `time_range` 不是数字毫秒区间，是 AOM 特有字符串格式 `startMs.endMs.durationMin`；`-1` 是占位符，由上游计算实际值
5. AOM 响应里 `errorCode = SVCSTG_AMS_2000000` 是历史遗留的"成功"码，HTTP 200 时 adapter **不要**把它当业务错误
6. `statistics` 字段在请求里是 `List<String>`，但在 datapoint 响应里是 `List<StatisticValue>`（含 `statistic` + `value`）；两者形状不同，转换时注意
7. AOM 调用需要 `projectId`（凭证里）——CES 不需要这个，环境变量 `HUAWEICLOUD_PROJECT_ID` 缺失会启动失败（详见 list_aom_metrics_v0.2.md §5）

## 5. 非功能要求

- **限流**: 复用 `aom-readonly` RateLimiter（与 `list_aom_metrics` 共享配额，QPS=10）
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标: `mcp_tool_invocation{tool="query_aom_metric_data", result="...", error_code="..."}`
  - 日志 INFO: `namespace` / `metricName` / `period` / `timeRange` / 耗时 / upstream trace id

## 6. 测试策略（Definition of Done）

### 单元测试

Tool 层（建议 `AomMetricDataToolTest`，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | 全合法请求透传 | service 收到对齐的 `AomQueryMetricDataRequest` |
| UT-02 | service 抛 InvalidParamException | 转 ErrorResponse，INVALID_PARAM，retryable=false |
| UT-03 | service 抛 UpstreamException | 转 ErrorResponse，含 trace id |

Service 层（建议 `AomMetricDataServiceTest`，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-S1 | namespace 缺失 / 格式非法 | INVALID_PARAM |
| UT-S2 | metric_name 缺失 | INVALID_PARAM |
| UT-S3 | period 不在允许集 | INVALID_PARAM |
| UT-S4 | time_range 不匹配正则 | INVALID_PARAM |
| UT-S5 | statistics 含未知值 | INVALID_PARAM |
| UT-S6 | fill_value 含未知值 | INVALID_PARAM |
| UT-S7 | dimensions 长度 21 | INVALID_PARAM |
| UT-S8 | 任一 dimension name/value 为空 | INVALID_PARAM |
| UT-S9 | 全合法 | 委托 adapter |

Adapter 层（建议 `AomMetricsAdapterImplTest` 新增方法，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-A1 | 全合法请求 | SDK Request 字段对齐（`QuerySample.namespace` / `metricName` / `dimensions`；`body.period` / `timeRange` / `statistics`；`request.fillValue`） |
| UT-A2 | `dimensions=null` | SDK `QuerySample.dimensions` 不被 set |
| UT-A3 | `statistics=null` 或空 | SDK `body.statistics` 不被 set |
| UT-A4 | `fillValue=null` | `request.setFillValue` 不被调用 |
| UT-A5 | SDK 返回空 samples | DTO series=[] |
| UT-A6 | SDK 返回 1 条 series 多 datapoints | namespace / metricName / dimensions / datapoints / statistics 字段全部透传 |
| UT-A7 | SDK 抛 429 | 重试后 UPSTREAM_THROTTLED |
| UT-A8 | SDK 抛 401 | 不重试 UPSTREAM_AUTH_FAILED |

### 类型契约测试（建议补，本期未交付）

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `QuerySample` 反射 | 含 namespace / metricName / dimensions |
| TC-02 | SDK `QuerySampleParam` 反射 | 含 samples / period / timeRange / statistics |
| TC-03 | SDK `ListSampleRequest` 反射 | 含 body / fillValue（fillValue 在顶层） |
| TC-04 | SDK `MetricDataPoints` 反射 | 含 timestamp / unit / statistics |
| TC-05 | SDK `StatisticValue` 反射 | 含 statistic / value |
| TC-06 | 文档样例 JSON 反序列化为 `ListSampleResponse` | 字段非 null |

### 部署后冒烟（贵阳环境，本期未交付，后续补）

`scripts/smoke/smoke-query_aom_metric_data.sh`:

1. `namespace=PAAS.CONTAINER`, 已知 `appName` 维度，`period=60`, `timeRange=-1.-1.60`, `statistics=[average]` → 返回 series 非空
2. `period=42` → INVALID_PARAM（Service 层拦截，不打上游）
3. `time_range=invalid` → INVALID_PARAM

## 7. 验收标准（DoD）

- [x] 代码已合入 master（提交 `4c346d6`），含 Tool / Service / Adapter / DTO 全栈
- [x] MCP Inspector 能看到 `query_aom_metric_data`，description 正确
- [x] 复用 `aom-readonly` RateLimiter
- [x] 日志含 namespace / metricName / period / timeRange / 耗时 / upstream trace id
- [x] Checkstyle 0 violations
- [ ] Tool / Service / Adapter 层 UT 全部通过（后续补）
- [ ] TC-01~06 类型契约测试通过（后续补）
- [ ] 贵阳环境冒烟脚本通过
- [ ] Micrometer 指标 `mcp_tool_invocation` 可见
- [ ] README 含 tool 使用示例
