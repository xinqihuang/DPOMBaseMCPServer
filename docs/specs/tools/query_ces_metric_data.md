# Spec: query_ces_metric_data

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 拉取 **单条 CES 指标** 在指定时间区间、聚合粒度下的数据点序列，得到实际监控数值（max / min / average / sum / variance）。

典型场景:
- Agent 收到告警，要看告警对象在告警时刻附近的指标曲线
- Agent 做单实例容量分析：取过去 1 小时 `cpu_util` 的均值
- Agent 验证已下发的扩容/重启动作是否生效：对比动作前后单指标曲线

定位:
- 前置：`list_ces_metrics` 用来发现合法的 `(namespace, metric_name, dimensions)` 三元组
- 互补：`batch_query_ces_metric_data` 是批量版（≥ 2 条建议改用批量，详见 ADR-004）

## 2. 范围边界

**做**:
- 查询单条 CES 指标的数据点列表（按 `period` 聚合）
- 支持 1–4 个维度过滤（`dim.0` … `dim.3`，按数组顺序映射）
- 单租户固定 region

**不做**:
- 不支持单次查询多条指标（用 `batch_query_ces_metric_data`）
- 不返回指标元数据（unit 跟在 datapoint 上，不另起 metric 元信息块）
- 不做客户端聚合 / 重采样
- 不做跨 region / 跨 projectId

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `query_ces_metric_data`
- description（Agent 看到的）:

  > Query monitoring data points of a single CES (Cloud Eye Service) metric for a
  > given Huawei Cloud resource over a time range. Returns aggregated values
  > (max / min / average / sum / variance) per period bucket. Call
  > list_ces_metrics first to discover available metric names and dimensions.
  > 'from'/'to' are UNIX timestamps in milliseconds; 'period' is the aggregation
  > granularity in seconds (1 / 60 / 300 / 1200 / 3600 / 14400 / 86400).

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `namespace` | string | 是 | — | CES namespace，例如 `SYS.ECS`；正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$` |
| `metric_name` | string | 是 | — | 精确指标名，例如 `cpu_util` |
| `dimensions` | array<{name, value}> | 是 | — | 1–4 个维度，按数组顺序映射到 CES `dim.0` … `dim.3`；每个 name/value 必须非空 |
| `filter` | string | 是 | — | 聚合方式：`average` / `max` / `min` / `sum` / `variance` |
| `period` | int | 是 | — | 聚合粒度（秒）：1 / 60 / 300 / 1200 / 3600 / 14400 / 86400 |
| `from` | long | 是 | — | 起始时间，毫秒级 UNIX 时间戳 |
| `to` | long | 是 | — | 结束时间，毫秒级 UNIX 时间戳，**必须严格大于** `from` |

**类型层面的强约束**（详见 ADR-004，T14 提交 `ce6fd6c` 重构后生效）:
- `filter` 在 DTO 中使用 `CesMetricFilter` 枚举（5 个值，严格）
- `period` 在 DTO 中使用 `CesMetricPeriod` 枚举（7 个值，严格）
- Tool 层把字符串/整数入参解析为枚举；未知值由 `CesMetricFilter.fromValue` / `CesMetricPeriod.fromSeconds` 抛 `IllegalArgumentException`，Tool 层 catch 后转 `InvalidParamException`

**输入校验规则**（按 Tool/Service 两层）:
- Tool 层：`filter` / `period` 为 null 或不可解析 → `INVALID_PARAM`，不调 service
- Service 层：
  - `namespace` 缺失或正则不匹配 → `INVALID_PARAM`
  - `metric_name` 缺失 → `INVALID_PARAM`
  - `dimensions` 为空或长度 > 4 → `INVALID_PARAM`
  - 任一 dimension 的 name/value 为空 → `INVALID_PARAM`
  - `from >= to` → `INVALID_PARAM`

### 3.3 输出契约（成功）

```json
{
  "metric_name": "cpu_util",
  "datapoints": [
    {
      "timestamp": 1700000000000,
      "unit": "%",
      "max": null,
      "min": null,
      "average": 23.5,
      "sum": null,
      "variance": null
    }
  ]
}
```

字段说明:
- `metric_name` 来自 SDK 响应，可能为 `null`（极端情况下上游不回填）
- `datapoints[]` 按时间升序；CES 会按 `period` 向前取整 `from`，实际返回点数可能略多于预期（上游行为）
- 未选择的聚合统计字段保持为 `null`
- 单个 datapoint 的 `unit` 来自上游对应 datapoint 字段，按 CES 行为是该指标的单位

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
- **SDK 方法**: `showMetricData(ShowMetricDataRequest)`
- **SDK 版本**: v3.1.177（详见 `CLAUDE.md` §1）

**字段映射**:

| MCP 输入 | SDK 字段 |
|---|---|
| `namespace` | `ShowMetricDataRequest.withNamespace(String)` |
| `metric_name` | `ShowMetricDataRequest.withMetricName(String)` |
| `filter` | `withFilter(ShowMetricDataRequest.FilterEnum.fromValue(filter.getValue()))` |
| `period` | `withPeriod(ShowMetricDataRequest.PeriodEnum.fromValue(period.getSeconds()))` |
| `from` | `withFrom(Long)` |
| `to` | `withTo(Long)` |
| `dimensions[0]` | `setDim0("<name>,<value>")` |
| `dimensions[1]` | `setDim1("<name>,<value>")` |
| `dimensions[2]` | `setDim2("<name>,<value>")` |
| `dimensions[3]` | `setDim3("<name>,<value>")` |

**AI 容易写错的点**（实现时务必注意）:
1. `ShowMetricData` 的维度参数是 4 个独立字段 `dim0`…`dim3`，**不是数组**，按 `dimensions` 数组顺序依次填入；超过 4 个由 Service 层先拦下，Adapter 层 `switch` 的 `default` 分支只是兜底
2. 每个 `dimX` 是 `"key,value"` 拼接的字符串，**不是对象**——与 `batch_query_ces_metric_data` 用结构化 `MetricsDimension` 的写法不同
3. `ShowMetricDataRequest.PeriodEnum.fromValue(int)` 接收 **整数**；`BatchListMetricDataRequestBody.PeriodEnum.fromValue(String)` 接收 **字符串**——同一个 SDK 在两套 API 上类型不一致，不要混淆
4. SDK 响应类是 `ShowMetricDataResponse`，数据点元素是 `Datapoint`（与批量版的 `DatapointForBatchMetric` 是不同类）
5. `Datapoint.getUnit()` 是 datapoint 自带字段，与 `list_ces_metrics` 里的 `metric.unit` 来源不同；本 tool 直接透传，不做合并

## 5. 非功能要求

- **限流**: 复用 `ces-readonly` RateLimiter（与 `list_ces_metrics` / `batch_query_ces_metric_data` 共享配额）
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标: `mcp_tool_invocation{tool="query_ces_metric_data", result="...", error_code="..."}`
  - 日志 INFO: `namespace` / `metricName` / `filter` / `period` / `from` / `to` / 耗时 / upstream trace id

## 6. 测试策略（Definition of Done）

### 单元测试

Tool 层（`CesMetricDataToolTest`，已交付 7 条）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | filter/period 字符串成功解析为枚举 | service 收到 `CesMetricFilter.AVERAGE` / `CesMetricPeriod.MIN_5`，请求字段透传 |
| UT-02 | filter 为 null | INVALID_PARAM，不调 service，errorMessage 含 "filter" |
| UT-03 | period 为 null | INVALID_PARAM，不调 service，errorMessage 含 "period" |
| UT-04 | filter 未知（如 `median`） | INVALID_PARAM，errorMessage 含取值 |
| UT-05 | period 未知（如 `42`） | INVALID_PARAM，errorMessage 含取值 |
| UT-06 | service 抛 InvalidParamException（如 `from >= to`） | 转 ErrorResponse，INVALID_PARAM，retryable=false |
| UT-07 | service 抛 UpstreamException（如 429） | 转 ErrorResponse，含 trace id，retryable=true |

Service 层（建议 `CesMetricDataServiceTest`，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-S1 | namespace 缺失 / 格式非法 | INVALID_PARAM |
| UT-S2 | metric_name 缺失 | INVALID_PARAM |
| UT-S3 | dimensions=null / 空 | INVALID_PARAM |
| UT-S4 | dimensions 长度 5 | INVALID_PARAM |
| UT-S5 | 任一 dimension name/value 为空 | INVALID_PARAM |
| UT-S6 | from >= to | INVALID_PARAM |
| UT-S7 | 全合法 | 委托 adapter 调用 |

Adapter 层（建议 `CesMetricsAdapterImplTest` 新增方法，本期未交付，后续补）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-A1 | 全合法请求 | SDK Request 字段对齐（namespace / metricName / filter / period / from / to） |
| UT-A2 | dimensions 1–4 个 | SDK `dim0`…`dim3` 按顺序填入 `"name,value"` 字符串 |
| UT-A3 | SDK 返回空 datapoints | DTO datapoints=[] |
| UT-A4 | SDK 返回多 datapoints | 字段透传，unit/聚合统计字段对齐 |
| UT-A5 | SDK 抛 429 | 重试 3 次后 UPSTREAM_THROTTLED |
| UT-A6 | SDK 抛 401 | 不重试 UPSTREAM_AUTH_FAILED |

### 类型契约测试（建议补，本期未交付）

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `ShowMetricDataRequest` 反射 | 含 namespace / metricName / filter / period / from / to / dim0 / dim1 / dim2 / dim3 |
| TC-02 | SDK `Datapoint` 反射 | 含 timestamp / unit / max / min / average / sum / variance |
| TC-03 | SDK `FilterEnum` / `PeriodEnum` 枚举常量 | 与本 spec §3.2 枚举一一对应 |
| TC-04 | 文档样例 JSON 反序列化为 `ShowMetricDataResponse` | 字段非 null |

### 部署后冒烟（贵阳环境，本期未交付，后续补）

`scripts/smoke/smoke-query_ces_metric_data.sh`:

1. 已知 ECS 实例 `cpu_util` 最近 1 小时 `period=300, filter=average` → 返回 datapoints 非空
2. period=42 → INVALID_PARAM（Tool 层拦截，不打上游）
3. from >= to → INVALID_PARAM

## 7. 验收标准（DoD）

- [x] Tool 层 UT-01~07 全部通过（7 条，见 `CesMetricDataToolTest`）
- [ ] Service 层 UT-S1~7 全部通过（后续补 `CesMetricDataServiceTest`）
- [ ] Adapter 层 UT-A1~6 全部通过（后续补 `CesMetricsAdapterImplTest` 新增方法）
- [ ] TC-01~04 类型契约测试全部通过（后续补）
- [x] MCP Inspector 能看到 `query_ces_metric_data`，description 正确
- [x] 复用 `ces-readonly` RateLimiter
- [x] 日志含 namespace / metricName / filter / period / from / to / 耗时 / upstream trace id
- [ ] Micrometer 指标 `mcp_tool_invocation` 可见
- [ ] 贵阳环境冒烟脚本通过
- [ ] README 含 tool 使用示例
- [x] Checkstyle 0 violations
