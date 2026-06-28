## ADDED Requirements

### Requirement: 单条 CES 指标数据点查询

系统 SHALL 提供只读工具 `query_ces_metric_data`，调用 CES SDK `ShowMetricData`（`CesClient.showMetricData`），按 `namespace` / `metric_name` / `dimensions` / `filter` / `period` / `from` / `to` 查询单条 CES 指标的数据点序列，并返回 `{ metric_name, datapoints[] }`。`datapoints[]` 每项 MUST 平铺 `timestamp` / `unit` / `max` / `min` / `average` / `sum` / `variance`，未选择的聚合统计字段保持 `null`。该工具 MUST 为只读（`readOnlyHint=true`、`idempotentHint=true`），不查询多条指标，不返回 metric 元信息块，不做客户端聚合 / 重采样 / 排序，不跨 region / projectId。

#### Scenario: 全合法入参透传查询
- **WHEN** Agent 传入合法的 `namespace` / `metric_name` / 1-4 个 `dimensions` / `filter` / `period` / `from < to`
- **THEN** 全部入参 SHALL 装配到 SDK `ShowMetricDataRequest`
- **AND** 返回 SDK 响应的 `metric_name` 与升序 `datapoints[]`

#### Scenario: 维度按序映射为 dim0..dim3
- **GIVEN** `dimensions` 含 1-4 个 `{name, value}`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 按数组顺序把每个维度拼为 `"name,value"` 字符串填入 SDK `dim0` … `dim3`（独立字符串字段，非数组）

#### Scenario: 空数据点
- **WHEN** 上游返回空 datapoints
- **THEN** 系统 SHALL 返回 `datapoints` 为空数组而非 null

### Requirement: filter 与 period 枚举解析

系统 SHALL 在 Tool 层把字符串 `filter` 解析为 `CesMetricFilter` 枚举（`average` / `max` / `min` / `sum` / `variance`）、把整数 `period` 解析为 `CesMetricPeriod` 枚举（秒：`1` / `60` / `300` / `1200` / `3600` / `14400` / `86400`）。`filter` 或 `period` 为 null 或不可解析时，系统 SHALL 返回 `INVALID_PARAM` 且不调用 service 层；错误信息只取异常 message，不附整个堆栈。

#### Scenario: filter 字符串成功解析
- **WHEN** 传入 `filter="average"` 且 `period=300`
- **THEN** service 层 SHALL 收到 `CesMetricFilter.AVERAGE` 与对应 `CesMetricPeriod`，请求字段透传

#### Scenario: filter 缺失
- **WHEN** `filter` 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 service，`error_message` 含 "filter"

#### Scenario: period 缺失
- **WHEN** `period` 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不调用 service，`error_message` 含 "period"

#### Scenario: filter 取值未知
- **WHEN** `filter` 为非枚举值（如 `median`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，`error_message` 含该取值

#### Scenario: period 取值未知
- **WHEN** `period` 为非枚举值（如 `42`）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，`error_message` 含该取值

### Requirement: Service 层输入校验

系统 SHALL 在 service 层校验：`namespace` MUST 非空且匹配正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`；`metric_name` MUST 非空；`dimensions` MUST 非空且长度 ∈ [1, 4]，每个维度的 name 与 value MUST 非空；`from` MUST 严格小于 `to`。任一校验失败 MUST 返回 `INVALID_PARAM` 且不发起上游调用。

#### Scenario: namespace 缺失或格式非法
- **WHEN** `namespace` 缺失或不匹配正则
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: dimensions 越界
- **WHEN** `dimensions` 为空或长度 > 4
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 维度 name/value 为空
- **WHEN** 任一 dimension 的 name 或 value 为空
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 时间窗非法
- **WHEN** `from >= to`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 上游异常映射

系统 SHALL 将 CES SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 MUST 携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 HTTP 429 / SDK throttling
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 服务端错误映射
- **WHEN** 上游返回 5xx
- **THEN** 系统 SHALL 返回 `UPSTREAM_ERROR`，`retryable=true`

#### Scenario: 超时映射
- **WHEN** 调用超时
- **THEN** 系统 SHALL 返回 `TIMEOUT`，`retryable=true`
