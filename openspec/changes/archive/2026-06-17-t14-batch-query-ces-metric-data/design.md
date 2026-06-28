## Context

存量工具回填。原始 spec：`docs/specs/tools/batch_query_ces_metric_data.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T14-batch-query-ces-metric-data.md`（状态 Done，提交 `ce6fd6c`）；关联决策：`docs/decisions/ADR-004-ces-enum-catalog.md`。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类/方法/版本、枚举分档、字段映射、不一致的时间参数格式、非功能要求、AI 易错点。

## Goals / Non-Goals

**Goals:**
- 在一次请求里无损暴露 CES `BatchListMetricData` 的批量取数能力（1–500 条指标，共享 filter/period/from/to）。
- 将 `filter` / `period` 改为类型安全的严格枚举，消除散落在 Service 层的 `Set<String>` 校验；`namespace` / `metric_name` / 维度名用宽容目录枚举作参考但不锁死 String 透传。
- 作为 `query_ces_metric_data` 的批量替代物：多条查询（≥ 2）一律推荐本工具。

**Non-Goals:**
- 不支持每条指标各自不同的 filter / period / 时间区间（受 CES 上游约束）。
- 不支持单次超过 500 条（上游硬限制）。
- 不做客户端聚合 / 重采样 / 跨 region / 跨 projectId。
- 不做 AOM / APM 的批量查询；不做缓存层；不把 `namespace` / `metric_name` 锁成严格枚举（ADR-004 决议）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**：`batchListMetricData(BatchListMetricDataRequest)`
- **SDK 版本**：v3.1.177（实际部署版本，字段缺失先怀疑版本）
- **CES API 文档**：https://support.huaweicloud.com/intl/en-us/api-ces/

**请求字段映射**：

| MCP 输入 | SDK 字段 |
|---|---|
| `metrics[i].namespace` | `body.metrics[i].withNamespace(String)` |
| `metrics[i].metric_name` | `body.metrics[i].withMetricName(String)` |
| `metrics[i].dimensions[j]` | `body.metrics[i].dimensions[j] = new MetricsDimension().withName(...).withValue(...)` |
| `filter` | `body.withFilter(Filter.fromValue(request.filter().getValue()))` |
| `period` | `body.withPeriod(BatchListMetricDataRequestBody.PeriodEnum.fromValue(String.valueOf(request.period().getSeconds())))` |
| `from` | `body.withFrom(Long)` |
| `to` | `body.withTo(Long)` |

**响应字段映射**（`BatchMetricData` → DTO `CesBatchMetricResult`）：

| SDK | DTO | 说明 |
|---|---|---|
| `namespace` | `namespace` | String |
| `metricName` | `metricName` | String |
| `dimensions[]` | `dimensions[]` | {name, value}，顺序与输入一致 |
| `unit` | `unit` | 在父级 `metrics[].unit` 上 |
| `datapoints[]` (`DatapointForBatchMetric`) | `datapoints[]` | `timestamp` + `max/min/average/sum/variance`，单 datapoint **不带 unit** |

> `metrics[]` 顺序与请求 `metrics[]` 顺序严格对齐；CES 会按 period 向前取整 `from`，点数可能略多于预期（上游行为）；未选择的聚合统计字段保持 `null`。

### 枚举分档（ADR-004）

- **严格枚举（DTO 直接持 enum）**
  - `CesMetricFilter`：`average` / `max` / `min` / `sum` / `variance`（5 值）；`@JsonValue getValue()` + `@JsonCreator fromValue(...)`，未知值抛 `IllegalArgumentException`。
  - `CesMetricPeriod`：秒数 1 / 60 / 300 / 1200 / 3600 / 14400 / 86400（7 值）；未知值抛 `IllegalArgumentException`。
  - Service 层不再持 `ALLOWED_FILTERS` / `ALLOWED_PERIODS` Set（由类型系统强制）。
- **宽容目录（DTO 仍持 String）**
  - `CesNamespace`（SYS.ECS / SYS.RDS / SYS.EVS 等）、`CesDimensionKey`（instance_id / disk_name 等）、`CesMetric`（ECS 基础监控 19 条，每条绑定 id/namespace/primaryDimension/unit/description）。
  - `fromValue(...)` 对未知值**返回 null（不抛异常）**，允许 Agent 透传新 SDK 值。

> 注：ADR-004 于 2026-06-11（T24）将 `CesNamespace` 升级为请求侧受控枚举；本变更回填的是 `ce6fd6c`（2026-06-02）当时的"宽容目录"状态，namespace 受控收紧由后续 change 承载。

### 校验分层

- **Tool 层**：`filter` / `period` 必填非空；字符串字面量解析为枚举（未知值 catch `IllegalArgumentException` → `InvalidParamException`）。
- **Service 层**：`from < to`；每条 `dimensions` 长度 [1, 4] 且 name/value 非空；`metrics` 长度 [1, 500]；`namespace` 正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`。

### 错误码 → retryable

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（Tool/Service 层） | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

失败响应携带 `error_message`、`upstream_trace_id`（华为云 `X-Request-Id`，可空）、`retryable`。

### 不一致的时间参数格式（重点，AI 易错）

CES `BatchListMetricData` 的 `from` / `to` 是 **UNIX 毫秒时间戳（Long）**，`period` 是 **聚合粒度秒数（int 语义，但 SDK 工厂接收字符串）**。与本服务其他工具的时间参数格式各不相同，调用方/实现方切勿混淆：

- 本工具：`from`/`to` = UTC 毫秒 Long；`period` = 秒数。
- APM `ListAlarmData`（`list_apm_alarm_data`）：`alarm_start_time`/`alarm_end_time` 为上游 **String**，未固定格式，原样透传。
- AOM / 部分上游：ISO8601 字符串。
- 部分趋势接口：`startMillis` / `endMillis` / `durationMinutes` 三元组。

实现时按各工具 spec 单独处理，不要跨工具复用时间解析逻辑。

### 非功能（限流 / 重试 / 超时 / 可观测）

- 限流：复用 `ces-readonly` RateLimiter（与 `list_ces_metrics` / `query_ces_metric_data` 共享配额）。
- 重试：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s。
- 超时：单次 SDK 调用 10s。
- 可观测：Micrometer `mcp_tool_invocation{tool="batch_query_ces_metric_data", result, error_code}`；INFO 日志含 `metricCount` + `filter` + `period` + `from` + `to` + 耗时 + upstream trace_id。

## Risks / Trade-offs

- **AI 易错点（来自 spec §4 + ADR-004）**：
  1. SDK `BatchListMetricDataRequestBody.PeriodEnum.fromValue(...)` **接收字符串**（如 `"300"`），不是 int → 要 `String.valueOf(period.getSeconds())`。
  2. Batch 的 dimensions 是结构化对象 `MetricsDimension`，不是 `ShowMetricData` 的 `dim0` 字符串拼接。
  3. 响应里 `unit` 在父级，单个 `DatapointForBatchMetric` 没有 unit 字段。
  4. SDK 响应类是 `BatchMetricData`，不要和 `list_ces_metrics` 的 `MetricInfoList` 混淆。
  5. Lambda 参数名长度 ≥ 2（Checkstyle `LambdaParameterName` `^\w{2,64}$`）：写 `dim -> ...` 不要写 `d -> ...`。
  6. `@JsonValue` + `@JsonCreator` 必须配对，否则 LLM 传字符串时反序列化失败。
  7. 宽容目录 `fromValue` 返回 null、严格枚举抛异常，两种语义不要混淆；Tool 层只对严格枚举 try/catch。
  8. `CesMetric` 不锁死 DTO，`metricName` 仍是 String，否则阻塞新 metric 透传。
- **String ↔ enum 转换成本**：同一字段在 Tool 层与 Adapter 层各转一次，已由测试覆盖。
- **遗留**：MCP `annotations`（readOnlyHint 等）在当前 Spring AI 版本未实际透出，仅为语义意图。
