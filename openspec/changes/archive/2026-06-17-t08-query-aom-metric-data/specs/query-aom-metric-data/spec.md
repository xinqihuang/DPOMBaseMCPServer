## ADDED Requirements

### Requirement: AOM 单条时序取值

系统 SHALL 提供只读工具 `query_aom_metric_data`，调用 AOM v2 SDK `ListSample`（POST，body = `QuerySampleParam`），按 `(namespace, metric_name, dimensions)` 拉取单条时序在指定 `time_range` / `period` 下的数据点序列，并返回 `{ series[] }`。

工具 MUST 暴露入参 `namespace` / `metric_name` / `dimensions` / `statistics` / `period` / `time_range` / `fill_value`，支持 maximum / minimum / sum / average / sampleCount 多统计组合。该工具 MUST 为只读（`readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`），不做多条时序批量查询、客户端聚合 / 重采样、跨 projectId / region。

调用前 SHOULD 先用 `list_aom_metrics` 发现合法的 namespace + metric_name + dimensions；工具 MUST NOT 编造或推断 namespace / metric_name / dimensions 入参。

#### Scenario: 合法请求透传上游
- **WHEN** Agent 传入合法的 `namespace` / `metric_name` / `period` / `time_range` 及可选 `dimensions` / `statistics` / `fill_value`
- **THEN** `namespace` / `metric_name` / `dimensions` SHALL 装配到 SDK `QuerySample`，`period` / `time_range` / `statistics` 装配到 body `QuerySampleParam`
- **AND** `fill_value` SHALL 装配到 `ListSampleRequest` 顶层（非 body）
- **AND** 返回 `series[]`，每条 series 含 namespace / metric_name / dimensions / datapoints

#### Scenario: time_range 原样透传
- **GIVEN** `time_range` 为 AOM 特有字符串 `startMillis.endMillis.durationMinutes`（如 `-1.-1.60`）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传到 `body.timeRange`，不做本地毫秒区间转换
- **AND** `-1` 占位符的实际值由上游计算

#### Scenario: dimensions 为空不装配
- **GIVEN** `dimensions` 为 null 或空
- **WHEN** 调用工具
- **THEN** 系统 SHALL NOT 调用 `QuerySample.setDimensions`

#### Scenario: statistics 为空由上游决定
- **GIVEN** `statistics` 为 null 或空
- **WHEN** 调用工具
- **THEN** 系统 SHALL NOT 调用 `body.setStatistics`，由上游决定默认统计方式

### Requirement: 输入校验

系统 SHALL 在 Service 层校验入参，违反任一规则 MUST 返回 `INVALID_PARAM`（`retryable=false`）且不发起上游调用：`namespace` 非空且匹配 `^(PAAS\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$`；`metric_name` 非空；`period` ∈ {60, 300, 900, 3600}；`time_range` 匹配 `^(-1|\d{1,16})\.(-1|\d{1,16})\.\d{1,7}$`；`statistics` 每个元素 ∈ {maximum, minimum, sum, average, sampleCount}；`fill_value`（若提供）∈ {-1, 0, null, average}；`dimensions` 长度 ≤ 20 且每个 name/value 非空。period / statistics MUST 在 Service 层用 `Set<>` 校验，而非 DTO 层枚举。

#### Scenario: namespace 缺失或格式非法
- **WHEN** `namespace` 缺失或不匹配 namespace 正则
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: metric_name 缺失
- **WHEN** `metric_name` 缺失
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: period 不在允许集
- **WHEN** `period` 缺失或不在 {60, 300, 900, 3600}
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: time_range 不匹配正则
- **WHEN** `time_range` 缺失或不匹配 `^(-1|\d{1,16})\.(-1|\d{1,16})\.\d{1,7}$`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: statistics 含未知值
- **WHEN** `statistics` 任一元素不在 {maximum, minimum, sum, average, sampleCount}
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: fill_value 含未知值
- **WHEN** `fill_value` 提供但不在 {-1, 0, null, average}
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: dimensions 越界或空 name/value
- **WHEN** `dimensions` 长度 > 20，或任一 dimension 的 name / value 为空
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 响应数据点投影

响应 DTO SHALL 将 SDK `ListSampleResponse` 无损投影为 `{ series[] }`：每条 `AomSampleSeries` 的 namespace / metric_name / dimensions 来自 `SampleDataValue.getSample()`（`QuerySample`），datapoints 来自 `SampleDataValue.getDataPoints()`。每个 `AomMetricDatapoint` 含 `timestamp`（Long）/ `unit`（String）/ `statistics`（`List<AomStatisticValue>`，每项为 `{statistic, value}` 对，**非平铺 max/min**）。SDK 响应中 `errorCode = SVCSTG_AMS_2000000` MUST NOT 被当作业务错误。

#### Scenario: 多 datapoint 全字段透传
- **WHEN** SDK 返回 1 条 series 含多个 datapoint
- **THEN** namespace / metric_name / dimensions / datapoints / 各 datapoint 的 timestamp / unit / statistics 对 SHALL 全部透传
- **AND** datapoint 的 `statistics` SHALL 映射为 `{statistic, value}` 列表

#### Scenario: 空 samples
- **WHEN** SDK 返回空 `samples`
- **THEN** 系统 SHALL 返回 `series = []`

#### Scenario: 历史成功码不当错误
- **GIVEN** HTTP 200 且响应 `errorCode = SVCSTG_AMS_2000000`
- **WHEN** adapter 解析响应
- **THEN** 系统 SHALL 正常返回 series，不视为业务错误

### Requirement: 上游异常映射

系统 SHALL 将 AOM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 SHALL 携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。系统 SHALL 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避重试。

#### Scenario: 限流映射
- **WHEN** 上游返回 HTTP 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 HTTP 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 服务端错误映射
- **WHEN** 上游返回 HTTP 5xx
- **THEN** 系统 SHALL 返回 `UPSTREAM_ERROR`，`retryable=true`

#### Scenario: 超时映射
- **WHEN** SDK 调用超时
- **THEN** 系统 SHALL 返回 `TIMEOUT`，`retryable=true`
