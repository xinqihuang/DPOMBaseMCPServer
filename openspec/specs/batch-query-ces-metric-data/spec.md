# batch-query-ces-metric-data Specification

## Purpose
一次请求批量拉取多个 CES 指标的时序数据，减少往返；指标与维度名必须取自 `list_ces_metrics`，禁止臆造。
## Requirements
### Requirement: CES 指标批量查询

系统 SHALL 提供只读工具 `batch_query_ces_metric_data`，调用 CES SDK `BatchListMetricData`，在一次请求中批量查询 1–500 条指标在指定时间区间、聚合粒度下的数据点，所有指标共享同一 `filter` / `period` / `from` / `to`，每条指标各自携带 `namespace` / `metric_name` / `dimensions`，返回结果 `metrics[]` 按请求 `metrics[]` 顺序对齐。该工具 MUST 为只读（`readOnlyHint=true`、`idempotentHint=true`），不做客户端聚合 / 重采样，不做跨 region / 跨 projectId。

#### Scenario: 批量查询成功透传
- **WHEN** Agent 传入 1–500 条合法 metrics 与合法 `filter` / `period` / `from` / `to`
- **THEN** 全部入参 SHALL 装配到 SDK `BatchListMetricDataRequestBody`（每条 `metrics[i]` 的 dimensions 为结构化 `MetricsDimension`）
- **AND** 返回的 `metrics[]` SHALL 与请求 `metrics[]` 顺序一致

#### Scenario: unit 在父级且 datapoint 不带 unit
- **WHEN** 上游返回带 datapoints 的指标
- **THEN** `unit` SHALL 位于父级 `metrics[].unit`
- **AND** 每个 datapoint 的 `unit` SHALL 为 null，未选择的聚合统计字段（max/min/average/sum/variance 中未请求者）SHALL 为 null

### Requirement: 发现链调用顺序

调用方在批量查询前 SHALL 先调用 `list_ces_metrics` 发现可用的 `metric_name` 与 `dimensions`，再用发现结果拼装 `metrics[]`。系统 SHALL NOT 编造未经发现的 `metric_name` / `dimensions` 入参。

#### Scenario: 先发现后查询
- **GIVEN** Agent 不确定某资源可用的 metric_name 或 dimensions
- **WHEN** 准备批量查询
- **THEN** Agent SHALL 先调用 `list_ces_metrics` 获取合法 metric_name 与 dimensions
- **AND** SHALL NOT 凭先验臆造入参直接调用 `batch_query_ces_metric_data`

### Requirement: filter 与 period 严格枚举校验

系统 SHALL 在 Tool 层将 `filter` / `period` 字符串解析为严格枚举（`CesMetricFilter` / `CesMetricPeriod`）：未提供（null）或不在枚举集内 MUST 返回 `INVALID_PARAM` 且不发起上游调用，错误信息 MUST 含违规字段或取值。`filter` 合法取值为 `average` / `max` / `min` / `sum` / `variance`；`period` 合法取值为秒数 `1` / `60` / `300` / `1200` / `3600` / `14400` / `86400`。

#### Scenario: filter 字符串成功解析为枚举
- **WHEN** 传入合法 `filter`（如 `max`）与 `period`（如 `3600`）
- **THEN** service SHALL 收到对应枚举值（`CesMetricFilter.MAX` / `CesMetricPeriod.HOUR_1`）

#### Scenario: filter 缺失
- **WHEN** `filter` 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 service，错误信息含 "filter"

#### Scenario: period 缺失
- **WHEN** `period` 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 service，错误信息含 "period"

#### Scenario: filter 取值未知
- **WHEN** `filter` 取值不在枚举集内（如 `p95`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，错误信息含该取值

#### Scenario: period 取值未知
- **WHEN** `period` 取值不在枚举集内（如 `7`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，错误信息含该取值

### Requirement: 业务输入校验

系统 SHALL 在 service 层校验：`from` MUST 严格小于 `to`；`metrics` 长度 MUST ∈ [1, 500]；每条 `metrics[i].dimensions` 长度 MUST ∈ [1, 4] 且每个 name/value 非空；每条 `metrics[i].namespace` MUST 匹配正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`。任一校验失败 MUST 返回 `INVALID_PARAM`。

#### Scenario: metrics 为空或超限
- **WHEN** `metrics` 为空或长度 > 500
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 时间区间非法
- **WHEN** `from >= to`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: namespace 格式非法
- **WHEN** 任一 `metrics[i].namespace` 不匹配正则
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: dimensions 长度越界或含空值
- **WHEN** 任一 `metrics[i].dimensions` 长度不在 [1, 4]，或含空 name/value
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 时间参数格式约定

系统 SHALL 将 `from` / `to` 视为 UTC 毫秒级 UNIX 时间戳（Long），`period` 视为聚合粒度秒数；系统 SHALL NOT 跨工具复用其他时间格式（如 APM 的上游字符串、AOM 的 ISO8601、趋势接口的 startMillis/endMillis/durationMinutes）解析本工具的时间入参。

#### Scenario: 毫秒时间戳透传
- **WHEN** 传入 `from` / `to` 为毫秒时间戳、`period` 为秒数
- **THEN** 系统 SHALL 原样装配为 SDK `from` / `to`（Long），并以 `String.valueOf(seconds)` 装配 SDK `PeriodEnum`

### Requirement: 上游异常映射

系统 SHALL 将 CES SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 MUST 携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。映射规则：429 → `UPSTREAM_THROTTLED`（retryable=true）；401/403 → `UPSTREAM_AUTH_FAILED`（retryable=false）；5xx → `UPSTREAM_ERROR`（retryable=true）；超时 → `TIMEOUT`（retryable=true）；序列化/未分类 → `INTERNAL`（retryable=false）。

#### Scenario: service 抛业务校验异常
- **WHEN** service 抛 `InvalidParamException`（如空 metrics）
- **THEN** 系统 SHALL 转为失败响应，`error_code=INVALID_PARAM`，`retryable=false`

#### Scenario: 上游 5xx 映射
- **WHEN** service 抛 UpstreamException（HTTP 5xx）
- **THEN** 系统 SHALL 返回 `UPSTREAM_ERROR`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 上游限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`

#### Scenario: 上游鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

