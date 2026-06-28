## Context

存量工具回填。原始 spec：`docs/specs/tools/query_ces_metric_data.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T07-query-ces-metric-data.md`（状态 Done，实现 `4c346d6`，枚举重构 `ce6fd6c`）；关联决策：`docs/decisions/ADR-004-ces-enum-catalog.md`。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类/方法/版本、字段映射、枚举目录、错误码映射、非功能要求、时间参数格式约定与 AI 易错点。

## Goals / Non-Goals

**Goals:**
- 无损暴露 CES `ShowMetricData` 的单指标取数能力，按 period 聚合返回 max/min/average/sum/variance 数据点序列。
- 用 `CesMetricFilter` / `CesMetricPeriod` 枚举在 DTO 层锁死 filter/period 取值，Tool 层解析字符串/整数入参为枚举。
- 与 CES 诊断链 `list_ces_metrics`（发现）/ `batch_query_ces_metric_data`（批量）形成可衔接的取数环节。

**Non-Goals:**
- 单次查询多条指标（由 `batch_query_ces_metric_data` 负责）。
- 返回指标元数据 metric.unit（unit 跟在 datapoint 上透传，不另起元信息块）。
- 客户端聚合 / 重采样 / 排序。
- 跨 region / 跨 projectId。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**：`showMetricData(ShowMetricDataRequest)`
- **SDK 版本**：v3.1.177（钉死，详见 `CLAUDE.md` §1，字段缺失先怀疑版本）
- **响应类**：`ShowMetricDataResponse`，数据点元素 `Datapoint`（与批量版 `DatapointForBatchMetric` 是不同类，勿混淆）

**请求字段映射（MCP 输入 → SDK `ShowMetricDataRequest`）**：

| MCP 输入 | SDK 字段 | 说明 |
|---|---|---|
| `namespace` | `withNamespace(String)` | |
| `metric_name` | `withMetricName(String)` | |
| `filter` | `withFilter(FilterEnum.fromValue(filter.getValue()))` | 枚举的字符串值 |
| `period` | `withPeriod(PeriodEnum.fromValue(period.getSeconds()))` | **整数**入参 |
| `from` | `withFrom(Long)` | 毫秒级 UNIX 时间戳 |
| `to` | `withTo(Long)` | 毫秒级 UNIX 时间戳，严格大于 from |
| `dimensions[0..3]` | `setDim0/1/2/3("<name>,<value>")` | 4 个独立字符串字段，非数组 |

**响应字段映射（SDK `Datapoint` → DTO `CesDatapoint`，平铺透传）**：`timestamp` / `unit` / `max` / `min` / `average` / `sum` / `variance`；顶层 `metric_name` 来自 `ShowMetricDataResponse`（可能为 null）。未选择的聚合统计字段保持 null。

### 枚举目录（ADR-004）

- `CesMetricFilter`（5 值，严格）：`average` / `max` / `min` / `sum` / `variance`；`fromValue(String)` 未知值抛 `IllegalArgumentException`。
- `CesMetricPeriod`（7 值，严格，单位秒）：`1` / `60` / `300` / `1200` / `3600` / `14400` / `86400`；`fromSeconds(int)` 未知值抛 `IllegalArgumentException`。
- Tool 层 `parseFilter(String)` / `parsePeriod(Integer)` catch `IllegalArgumentException` → `InvalidParamException`，只取 `e.getMessage()`，不塞整个堆栈。

### 时间参数格式

- 本工具 `from` / `to` 为**毫秒级 UNIX 时间戳（Long）**，`to` 必须严格大于 `from`；`period` 为聚合粒度的**秒数（int）**。
- 注意同 SDK 跨 API 时间/枚举入参类型不一致：本工具 `ShowMetricDataRequest.PeriodEnum.fromValue(int)` 接收**整数**；而 `batch_query_ces_metric_data` 的 `BatchListMetricDataRequestBody.PeriodEnum.fromValue(String)` 接收**字符串**。实现时勿混淆。

### 校验分层

- Tool 层：`filter` / `period` 为 null 或不可解析 → `INVALID_PARAM`，不调 service。
- Service 层：`namespace` 缺失或不匹配正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$` / `metric_name` 缺失 / `dimensions` 空或长度 > 4 / 任一 dimension name·value 为空 / `from >= to` → `INVALID_PARAM`。filter/period 已是枚举，Service 仅检查非 null。

### 错误码映射

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（Tool/Service 层） | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

失败响应携带 `error_message` / `upstream_trace_id`（华为云 `X-Request-Id`，可空）/ `retryable`。

### AI 易错点

1. `dim0`…`dim3` 是 4 个独立字符串字段，**不是数组**，按 `dimensions` 数组顺序填入；超过 4 个由 Service 拦下，Adapter `switch` 的 `default` 仅兜底。
2. 每个 `dimX` 是 `"name,value"` 拼接的**字符串**，不是结构化对象（与批量版用 `MetricsDimension` 不同）。
3. `ShowMetricDataRequest.PeriodEnum.fromValue(int)` 接整数；批量版 `PeriodEnum.fromValue(String)` 接字符串——同 SDK 两套 API 类型不一致。
4. 响应类 `ShowMetricDataResponse`，数据点 `Datapoint`（不要与 `DatapointForBatchMetric` 混淆）。
5. `Datapoint.getUnit()` 来自 datapoint 自带字段，与 `list_ces_metrics` 的 `metric.unit` 来源不同，本工具直接透传不合并。
6. DTO 早期用 `String filter` / `Integer period` 配合 `Set` 校验，T14 重构为枚举后该校验集合移除，Service 只检查 null——回看 git history 注意 `CesMetricDataService` 在 `4c346d6` 与 `ce6fd6c` 两提交间形态不同。
7. `parsePeriod(Integer)` 入参可为 null，而 `CesMetricPeriod.fromSeconds(int)` 是 `int` 入参——拆箱前必须先判空，否则 NPE 被漏 catch 变成 INTERNAL。

## Risks / Trade-offs

- **非功能**：复用 `ces-readonly` RateLimiter（与 `list_ces_metrics` / `batch_query_ces_metric_data` 共享配额）；仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避重试（200ms / 800ms / 3.2s）；SDK 单次调用超时 10s；Micrometer `mcp_tool_invocation{tool="query_ces_metric_data"}`，INFO 日志含 namespace / metricName / filter / period / from / to / 耗时 / upstream trace id。
- **上游行为**：CES 按 period 向前取整 `from`，实际返回点数可能略多于预期；datapoints 按时间升序，调用方需容忍边界点。
- **遗留**：Service / Adapter 层 UT、类型契约测试、贵阳冒烟脚本、Micrometer 看板与 README 示例本期未交付（见 tasks.md 末尾遗留项）。MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
