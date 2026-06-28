# list-ces-metrics Specification

## Purpose
CES（云监控）指标定义发现工具：列出华为云某命名空间/维度下可用的指标元数据（namespace、metric_name、dimensions、unit），是 `query_ces_metric_data` 取数前的前置发现步骤；云基础设施指标（ECS/RDS/EVS 等）用本工具，应用层指标用 `list_aom_metrics`。
## Requirements
### Requirement: CES 指标定义查询

系统 SHALL 提供只读工具 `list_ces_metrics`，调用华为云 CES SDK `listMetrics(ListMetricsRequest)`，按上游过滤参数检索已注册的指标定义列表，返回 `{ metrics[], pagination }`。工具 MUST 暴露 7 个参数（`namespace` / `metric_name` / `dim_name` / `dim_value` / `limit` / `start` / `order`），全部可选（`limit` 默认 100、`order` 默认 `desc`）。该工具 MUST 为只读，仅返回指标元数据（namespace / metric_name / unit / dimensions），不返回指标数据点，不做多维度组合过滤。

#### Scenario: 全默认参数透传
- **WHEN** Agent 不传任何过滤参数调用工具
- **THEN** 系统 SHALL 以 `namespace` / `metric_name` 等为 null、`limit=100`、`order=desc` 装配 SDK 请求并调用上游

#### Scenario: 按 namespace 过滤
- **WHEN** 传入 `namespace=SYS.ECS`
- **THEN** SDK 请求的 `namespace` SHALL 为 `SYS.ECS`

#### Scenario: 单维度过滤拼接
- **WHEN** 同时传入 `dim_name=instance_id` 与 `dim_value=xxx`
- **THEN** SDK 请求的 `dim.0` SHALL 为字符串 `"instance_id,xxx"`（逗号分隔，非对象）

#### Scenario: 输出始终包含 unit
- **WHEN** 上游成功返回任意指标列表
- **THEN** 每个 `metrics[]` 元素 SHALL 包含 `unit` 字段

### Requirement: 输入校验

系统 SHALL 在 service 层校验输入，校验失败 MUST 返回 `INVALID_PARAM` 且不发起上游调用。校验规则：`dim_name` 与 `dim_value` MUST 要么都给要么都不给；`limit` MUST ∈ [1, 1000]；`namespace`（若提供）MUST 匹配 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`；`order` MUST 为 `asc` 或 `desc`。

#### Scenario: dim 参数不成对
- **WHEN** 只提供 `dim_name` 而不提供 `dim_value`（或反之）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: limit 越界
- **WHEN** `limit` > 1000 或 < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 SDK

#### Scenario: namespace 格式非法
- **WHEN** `namespace` 不匹配 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`（如小写开头 `syc.ecs`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: order 取值非法
- **WHEN** `order` 既非 `asc` 也非 `desc`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: marker 游标分页

系统 SHALL 以华为云 marker 游标方式分页：`start` 入参 SHALL 接受上一次响应返回的 `next_marker` 字符串（非数字 offset）并原样透传给 SDK。响应 SHALL 返回 `pagination{ count, total, next_marker, has_more }`，其中 `has_more` SHALL 等价于 `next_marker != null && count > 0`。

#### Scenario: 空结果分页
- **WHEN** 上游返回空指标列表
- **THEN** 系统 SHALL 返回 `metrics=[]`、`has_more=false`、`total=0`

#### Scenario: 满页带 marker
- **WHEN** 上游返回满页结果且携带 marker
- **THEN** 系统 SHALL 返回 `has_more=true`，且 `next_marker` 原样透传上游 marker

### Requirement: 响应字段投影

响应 DTO SHALL 将 SDK `MetricInfoList` / `MetricsDimension` / `MetaData` 投影为 `CesMetricInfo` / `CesMetricDimension` / `CesPagination`，覆盖 namespace / metric_name / unit / dimensions 及分页元数据。任一关键字段在 SDK 升版后改名或缺失 MUST 导致契约测试失败。

#### Scenario: 契约测试反序列化样例 JSON
- **GIVEN** 来自华为云文档的样例 `sdk-samples/ces/list-metrics-response.json`
- **WHEN** 反序列化为 SDK `ListMetricsResponse` 并经 adapter 映射
- **THEN** `metrics` SHALL 非空，`meta_data` 与其 `count` SHALL 非 null
- **AND** SDK `MetricInfoList` / `MetricsDimension` / `MetaData` 的关键字段反射断言 SHALL 通过

### Requirement: 上游异常映射

系统 SHALL 将 CES SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 SHALL 携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。仅 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 可重试，最多 3 次指数退避。

#### Scenario: 限流映射并重试
- **WHEN** 上游返回 429（或抛 `RequestNotPermitted`）
- **THEN** 系统 SHALL 触发重试，3 次后仍失败则返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`，不重试

#### Scenario: 服务端错误映射
- **WHEN** 上游返回 5xx
- **THEN** 系统 SHALL 重试 3 次后失败返回 `UPSTREAM_ERROR`，`retryable=true`

#### Scenario: 超时映射
- **WHEN** 单次 SDK 调用超时
- **THEN** 系统 SHALL 返回 `TIMEOUT`，`retryable=true`

