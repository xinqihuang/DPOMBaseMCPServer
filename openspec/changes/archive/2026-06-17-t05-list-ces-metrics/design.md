## Context

存量工具回填。原始 spec：`docs/specs/tools/list_ces_metrics.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T05-list-ces-metrics.md`。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 字段映射、DTO 设计、错误码映射、非功能要求、时间 / 分页参数约定与 AI 易错点。

`list_ces_metrics` 定位为 `query_metric_data` / `batch_query_metric_data` 的前置发现工具，仅返回指标定义元数据，不返回数据点。单租户固定 region，不做跨 region / 跨账号。

## Goals / Non-Goals

**Goals:**
- 无损暴露 CES `listMetrics` 的检索能力（按 namespace / metric_name / 单维度过滤 + marker 游标分页）。
- 返回稳定的指标元数据三元组（namespace / metric_name / dimensions）+ unit，供后续取数工具衔接。
- 在 service 层完成清晰的入参校验，失败给出结构化 `INVALID_PARAM`，不向上游发请求。

**Non-Goals:**
- 不返回指标数据点（`query_metric_data` 职责）。
- 不做 AOM Prometheus 指标（`list_aom_metrics`）。
- 不做多维度组合过滤（华为云 ListMetrics 仅支持 `dim.0` 单维度入参）。
- 不做跨 region / 跨账号；不做客户端聚合 / 排序。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**：`listMetrics(ListMetricsRequest)`
- **SDK 版本**：v3.1.196（字段缺失先怀疑版本）
- **相关 SDK 类型**：`ListMetricsRequest` / `ListMetricsResponse` / `MetricInfoList` / `MetricsDimension` / `MetaData`

**请求字段映射（MCP 输入 → SDK `ListMetricsRequest`）**：

| MCP 输入 | SDK Request 字段 |
|---|---|
| `namespace` | `withNamespace(String)` |
| `metric_name` | `withMetricName(String)` |
| `dim_name + dim_value` | `withDim0(String)`，格式 `"{dim_name},{dim_value}"` |
| `limit` | `withLimit(Integer)`（默认 100） |
| `start` | `withStart(String)`（marker 字符串） |
| `order` | `withOrder(String)`（默认 `desc`） |

**响应字段映射（SDK → DTO）**：

| SDK | DTO | 说明 |
|---|---|---|
| `MetricInfoList.namespace` | `CesMetricInfo.namespace` | |
| `MetricInfoList.metricName` | `CesMetricInfo.metricName` | |
| `MetricInfoList.unit` | `CesMetricInfo.unit` | 始终返回 |
| `MetricInfoList.dimensions[]` | `CesMetricInfo.dimensions[]` | `MetricsDimension{name,value}` → `CesMetricDimension{name,value}` |
| `MetaData.count` | `CesPagination.count` | meta 缺省时回落为 `metrics.size()` |
| `MetaData.total` | `CesPagination.total` | meta 缺省时回落为 `metrics.size()` |
| `MetaData.marker` | `CesPagination.nextMarker` | nullable，透传 |
| 派生 | `CesPagination.hasMore` | `nextMarker != null && count > 0` |

### DTO 设计

- 输入 `CesListMetricsRequest` 用 record，紧凑构造仅做规范化（`limit` 缺省 100、`order` 缺省 `desc`），**不在 DTO 构造抛异常**——否则 `IllegalArgumentException` 会绕过 `INVALID_PARAM` 错误码。所有业务校验下沉到 `CesMetricsService`。
- 输出全部 record：`CesListMetricsResponse(metrics, pagination)` / `CesMetricInfo(namespace, metricName, unit, dimensions)` / `CesMetricDimension(name, value)` / `CesPagination(count, total, nextMarker, hasMore)`。
- MCP 输出 JSON 采用 snake_case，与华为云风格一致（`spring.jackson.property-naming-strategy=SNAKE_CASE`），对齐主 spec §3.3。

### 错误码映射

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败 | `INVALID_PARAM` | false |
| HTTP 429 / SDK throttling / `RequestNotPermitted` | `UPSTREAM_THROTTLED` | true |
| HTTP 401 / 403 | `UPSTREAM_AUTH_FAILED` | false |
| HTTP 5xx | `UPSTREAM_ERROR` | true |
| 调用超时 | `TIMEOUT` | true |
| 序列化 / 未分类异常 | `INTERNAL` | false |

失败响应统一结构 `{ error_code, error_message, upstream_trace_id, retryable }`，`upstream_trace_id` 取华为云 `X-Request-Id`（可空）。MCP 层 catch `SmartomException` 转 `ErrorResponse`，不让 SDK 异常透传。

### 时间 / 分页参数约定

- 本工具**无时间窗参数**（不同于带 `start_time`/`end_time` 的告警 / trace / 日志类工具，也不同于 startMillis/durationMinutes 风格）。
- 分页是 **marker 游标式**：`start` 入参 = 上一次响应的 `next_marker` 字符串，**不是 offset 数字**。`has_more` 为客户端提供易用判断。

### 非功能

- 限流：`ces.listMetrics` 走 Resilience4j RateLimiter，key=`ces-readonly`，默认 10 QPS，可配置。
- 重试：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，最多 3 次，指数退避 200ms / 800ms / 3.2s（`huaweicloud-retryable` 组）。
- 超时：单次 SDK 调用 10s。
- 可观测：Micrometer `mcp_tool_invocation{tool="list_ces_metrics", result, error_code}`；INFO 日志含入参摘要 / 耗时 / 结果数 / upstream trace id。
- 启动校验：AK/SK 缺失或格式错时 Spring Boot fail-fast，不进入 ready。

## Risks / Trade-offs

- **AI 易错点**：
  1. `dim.0` 在 SDK 是 `Dim0` 字段，单字符串拼接 `"key,value"` 逗号分隔，**不是对象**。
  2. CES ListMetrics 只支持 `dim.0` 一个维度过滤入参（结果可能返回多维度，但过滤只能给一个）。
  3. `start` 是 marker 字符串，不是 offset 数字。
  4. namespace 校验自己在 service 层先拦（正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`），SDK 内部虽也可能校验，但要给清晰错误码。
  5. SDK v3 getter/setter 有 `setXxx`(void) 与链式 `withXxx`(return this) 两种风格，实现 / 测试以具体类方法签名为准，勿凭印象。
  6. `@ToolParam` 可选参数必须显式 `required=false`，否则 MCP schema 误标 required。
  7. record 默认可被 Jackson 序列化，但需确认命名策略，输出统一 snake_case。
- **遗留**：MCP `annotations`（readOnlyHint / idempotentHint 等）在当前 Spring AI `@Tool` 未实际透出，当前仅为语义意图。
- 类型契约测试（反射 + 样例 JSON）用于在 CI 暴露 SDK 升版导致的字段改名 / 删字段——Builder + 反射场景下编译期可能发现不了。
