## Context

存量工具回填。原始 spec：`docs/specs/tools/show_apm_trend.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T22-show-apm-trend.md`（状态 Done）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类/方法/版本、字段映射表、枚举映射、`Object` 值的保留约定、`latest_data_Time` 大小写约定、非功能要求（限流/重试/超时/可观测）、时间参数格式约定，以及 AI 易错点。

趋势数据来自 APM 自身指标存储，与 CES 的 metric_data 是两条独立链路，不要混淆。

## Goals / Non-Goals

**Goals:**
- 无损暴露 APM `ShowTrend` 的查询能力与全部响应字段（`FrontLine` 6 字段 × `FrontPoint` 2 字段 + `latest_data_Time`）。
- 请求体 `TrendParam` 全部 6 字段暴露，其中 `view_config`（SDK `TrendView`）作为嵌套 record 暴露，不拍平。
- 与 APM 诊断链 `list_apm_alarm_data` 衔接（先告警、后趋势）。

**Non-Goals:**
- 客户端时间窗对齐 / 聚合 / 折线渲染 / 排序。
- 解析 `FrontPoint.value` 内部结构（保留 `Object`，由 Agent 判断 Number/String）。
- 修正 SDK 字段名 `latest_data_Time` 的大小写（不碰 SDK JSON 注解）。
- 复用 `ApmAlarmAdapter` / `ApmAlarmService`（领域不同，独立模块）。
- 新增 RateLimiter（复用 `apm-readonly`）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`showTrend(ShowTrendRequest)`
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本）
- **HTTP**：`POST /v1/apm2/openapi/view/trend/show`，body = `TrendParam`，header = `x-business-id: Long`
- 不单独建 `ApmClient` Bean，复用既有 `apmClient` 并注入 `x-business-id` 头。

**请求映射（`ApmTrendRequest` → SDK）**：

| DTO `ApmTrendRequest` | SDK |
|---|---|
| businessId | `ShowTrendRequest.xBusinessId`（header） |
| viewConfig | `TrendParam.viewConfig`（嵌套对象，见下表） |
| instanceId | `TrendParam.instanceId` |
| monitorItemId | `TrendParam.monitorItemId` |
| envId | `TrendParam.envId` |
| startTime | `TrendParam.startTime` |
| endTime | `TrendParam.endTime` |

**viewConfig 子映射（`ApmTrendViewConfig` ↔ SDK `TrendView`，12 字段一一对应）**：`viewType` / `collectorName` / `metricSet` / `title` / `tableDirection` / `groupBy` / `filter` / `fieldItemList` / `span` / `spanField` / `orderBy` / `latest`。其中 `span` 为 `Boolean`、`precision` 为 `Integer`，保持 SDK 原类型。

**fieldItemList 元素（`ApmTrendFieldItem` ↔ SDK `FieldItem`，7 字段）**：`function` / `as` / `defaultValue` / `trace`(Boolean) / `precision`(Integer) / `unit` / `visible`(Boolean)。

**响应映射（全字段无损）**：

| SDK | DTO |
|---|---|
| `ShowTrendResponse.line_list` (`List<FrontLine>`) | `ApmTrendResponse.lineList` (`List<ApmTrendLine>`) |
| `ShowTrendResponse.latest_data_Time` (`Long`) | `latestDataTime` (`Long`，JSON 出参 `latest_data_time`) |
| `FrontLine.point_list` (`List<FrontPoint>`) | `ApmTrendLine.pointList` (`List<ApmTrendPoint>`) |
| `FrontLine.title / unit / precision / data_type / visible` | `title / unit / precision(Integer) / dataType / visible(Boolean)` |
| `FrontPoint.time` (`Long`) | `ApmTrendPoint.time` (`Long`) |
| `FrontPoint.value` (`Object`) | `ApmTrendPoint.value` (`Object`) |

### 枚举映射

`view_type` / `table_direction` 是 SDK 枚举。DTO 字段类型用 `String`（不把 SDK 枚举泄漏到 adapter 之外）；adapter 映射时用 `TrendView.ViewTypeEnum.fromValue(...)` / `TrendView.TableDirectionEnum.fromValue(...)` 反向映射。`fromValue` 抛 `IllegalArgumentException` → adapter 兜底包成 `InvalidParamException`（主校验在 service 层）。

### value 保留 Object

按 §4.1 无损投影准则，`FrontPoint.value` DTO 字段直接写 `Object value`，Jackson 序列化时按运行时类型输出（Long/Double/String）。契约测试 MUST 覆盖 Number + String 两种 value。

### latest_data_Time 大小写

SDK 原 JSON key 是 `latest_data_Time`（大写 T）。DTO Java 字段名写 `latestDataTime`，序列化输出 `latest_data_time`（统一 snake_case）；adapter 用 `sdkResp.getLatestDataTime()` 取值，不在 DTO 上加 `@JsonProperty("latest_data_Time")`，不暴露 SDK 拼写瑕疵到外部契约。

### 时间参数格式（不一致约定）

- 工具 description 对 Agent 声明 `start_time` / `end_time` 为 **ISO-8601 字符串**。
- service 层仅校验非空，不做格式解析或转换。
- adapter 将字符串**原样透传**给 SDK `TrendParam.startTime` / `endTime`（上游为 String，未在 SDK 层固定格式）。
- 响应侧 `FrontPoint.time` 与 `latest_data_Time` 为 **UTC 毫秒**（`Long`），不做本地时区转换。
- 注意：与并列工具的时间约定存在不一致——本工具入参为字符串（ISO-8601 声明、上游 String 透传），出参 time 为 UTC 毫秒；不要假设上游接受 `startMillis` / `endMillis` / `durationMinutes` 三元组。

### 非功能（限流 / 重试 / 超时 / 可观测）

- **限流**：复用 `apm-readonly` RateLimiter。
- **重试**：复用 `huaweicloud-retryable`，仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR`(5xx) / `TIMEOUT` 做 3 次指数退避。
- **超时**：SDK 传输层超时同 `list_apm_alarm_data`（10s）。
- **可观测**：Micrometer `mcp_tool_invocation{tool="show_apm_trend"}`；INFO 日志含 businessId / monitorItemId / viewType / 时间窗 / 耗时 / `upstreamTraceId`（华为云 `X-Request-Id`，可空）。

## Risks / Trade-offs

- **嵌套 view_config 而非拍平**：Spring AI MCP 从 record 反射 JSON Schema，Agent 看到嵌套对象。若拍平为 12 个外层 `@ToolParam`，`field_item_list` 这种 `List<复合对象>` 无法用扁平参数表达，schema 必崩。代价是 Agent 需理解嵌套结构。
- **`value` 为 `Object`**：保留无损但 Agent 需自行判断 Number/String，需在 description 与契约测试中明确。
- **遗留**：MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。

### AI 易错点

1. `FrontPoint.value` 必须保留 `Object`，不要私改成 `Number`/`String`（上游可能 Long/Double/String，假设其一会丢信息或 ClassCastException）。
2. `latest_data_Time` 含大写 `T`：DTO 字段名 `latestDataTime`，输出 `latest_data_time`，adapter 用 `getLatestDataTime()`，不碰 SDK 注解。
3. `view_type` / `table_direction` 是 SDK 枚举：DTO 用 `String`，adapter `fromValue` 兜底 catch 包成 `InvalidParamException`。
4. 不拍平 `view_config`（嵌套 record）。
5. `field_item_list` 可选：SDK 允许 null/空 list，DTO 用 `List<ApmTrendFieldItem>` 且允许 null，adapter `null` 透传给 SDK，不要换成空 list（"未指定" vs "显式空" 语义不同）。
6. 新模块独立，不复用 Alarm 的 adapter/service。
7. 不凭记忆改 SDK 字段类型：`precision: Integer` / `span: Boolean` / `time: Long` 等对照 SDK 源码保持原类型。
