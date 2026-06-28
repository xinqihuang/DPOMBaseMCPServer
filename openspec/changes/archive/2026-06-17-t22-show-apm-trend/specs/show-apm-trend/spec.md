## ADDED Requirements

### Requirement: APM 趋势数据查询

系统 SHALL 提供只读工具 `show_apm_trend`，调用 APM SDK `ShowTrend`（`POST /v1/apm2/openapi/view/trend/show`），按 `monitor_item_id` × `view_config` × 时间窗拉取监控项趋势数据（折线 / 汇总表 / 明细表），并返回 `{ line_list[], latest_data_time }`。

工具 MUST 暴露 6 个外层参数（`business_id` 头部参数 + `instance_id` / `monitor_item_id` / `env_id` / `start_time` / `end_time`）与 1 个嵌套对象参数 `view_config`（不拍平）。该工具 MUST 为只读（`readOnlyHint=true` / `destructiveHint=false` / `idempotentHint=true`），不做客户端聚合 / 时间窗对齐 / 曲线渲染 / 排序。

#### Scenario: 参数装配与返回透传
- **WHEN** Agent 传入合法的 7 个入参（含嵌套 `view_config`）
- **THEN** 5 个外层字段与嵌套 `view_config` SHALL 正确装配到 SDK `TrendParam`，`business_id` 装配到 `x-business-id` 头
- **AND** 返回 `line_list[]` 与 `latest_data_time`

#### Scenario: view_config 嵌套结构暴露
- **GIVEN** `view_config` 含 12 字段（含 `field_item_list` 之 7 字段元素）
- **WHEN** 工具向 Agent 暴露 JSON Schema
- **THEN** 系统 SHALL 以嵌套对象（非 12 个扁平参数）暴露 `view_config`
- **AND** `field_item_list` 为可空，缺省时 SHALL 原样以 null 透传给 SDK（不替换为空 list）

### Requirement: 时间参数原样透传

系统 SHALL 将 `start_time` / `end_time` 字符串原样透传给上游 SDK `TrendParam`，不做本地时间解析或格式转换；响应侧 `point_list[*].time` 与 `latest_data_time` 为 UTC 毫秒 `Long`，系统 SHALL 不做时区转换。

#### Scenario: 入参时间原样透传
- **GIVEN** `start_time` / `end_time` 为 ISO-8601 字符串（上游接收 String）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传，不做解析或格式转换

### Requirement: business_id 解析

当 `business_id` 头部参数为 null 时，系统 SHALL 回落到 `huaweicloud.apm-business-id` 配置默认值，并注入 `x-business-id` 头。

#### Scenario: business_id 回落配置默认值
- **GIVEN** 调用未传 `business_id`，但配置了 `huaweicloud.apm-business-id`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 用配置默认值注入 `x-business-id` 头

#### Scenario: business_id 与默认值均缺失
- **GIVEN** 调用未传 `business_id`，且 `huaweicloud.apm-business-id` 未配置
- **WHEN** 调用工具
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: 输入校验

系统 SHALL 在 service 层校验：`view_config` MUST 非 null；`view_config.view_type` MUST ∈ {`trend`, `sumtable`, `rawtable`}；`view_config.metric_set` MUST 非空；`start_time` / `end_time` MUST 非空。任一不满足 SHALL 返回 `INVALID_PARAM`，不发起上游调用。

#### Scenario: view_config 为 null
- **WHEN** `view_config == null`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: view_type 非法值
- **WHEN** `view_config.view_type` 不在 {`trend`, `sumtable`, `rawtable`}
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: metric_set 为空
- **WHEN** `view_config.metric_set` 为空
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 时间窗缺失
- **WHEN** `start_time` 或 `end_time` 缺失
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: 枚举兜底映射

adapter SHALL 将 `view_type` / `table_direction` 的 `String` 值经 SDK `TrendView.ViewTypeEnum.fromValue` / `TrendView.TableDirectionEnum.fromValue` 反向映射为 SDK 枚举。SDK 抛 `IllegalArgumentException` 时 adapter SHALL 兜底包成 `INVALID_PARAM`，不透传 SDK 异常。

#### Scenario: 非法枚举值兜底
- **GIVEN** 越过 service 校验的非法 `view_type` / `table_direction` 值
- **WHEN** adapter 执行 `fromValue` 映射
- **THEN** 系统 SHALL 捕获 `IllegalArgumentException` 并返回 `INVALID_PARAM`

### Requirement: ShowTrendResponse 无损投影

响应 DTO SHALL 无损覆盖 SDK `ShowTrendResponse`：`line_list[*]` 对齐 `FrontLine` 全 6 字段（`title` / `unit` / `precision` / `data_type` / `visible` / `point_list`），`point_list[*]` 对齐 `FrontPoint` 全 2 字段（`time` / `value`），顶层 `latest_data_Time` 映射为 `latest_data_time`。`FrontPoint.value` SHALL 保留 `Object` 类型，由 Jackson 按运行时类型序列化。任一字段缺失 MUST 导致契约测试失败。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `sdk-samples/apm/show-trend-response.json`（含 ≥2 条 `FrontLine`，每条 `point_list` ≥2 点）
- **WHEN** 反序列化为 SDK `ShowTrendResponse` 并经 adapter 映射
- **THEN** `line_list` 全 6 字段 × `point_list` 全 2 字段 × 顶层 `latest_data_time` SHALL 逐一断言通过
- **AND** 删除任一 DTO 字段 SHALL 导致编译或断言失败

#### Scenario: value 同时覆盖数字与字符串
- **GIVEN** 样本中某 `point.value` 为 Number，另一 `point.value` 为 String
- **WHEN** 经 adapter 映射并序列化
- **THEN** 两种类型 SHALL 均无损保留（Number 不转字符串、String 不转数字）

### Requirement: latest_data_time 命名归一

DTO 内部 Java 字段名 SHALL 为 `latestDataTime`，JSON 序列化输出 SHALL 为 `latest_data_time`（snake_case）。adapter SHALL 经 `sdkResp.getLatestDataTime()` 取值，不得在 DTO 上以 `@JsonProperty("latest_data_Time")` 暴露 SDK 大小写瑕疵。

#### Scenario: 外部契约统一 snake_case
- **WHEN** 响应序列化为 JSON
- **THEN** 顶层时间字段键名 SHALL 为 `latest_data_time`，而非 SDK 原拼写 `latest_data_Time`

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 服务端错误与超时映射
- **WHEN** 上游返回 5xx 或调用超时
- **THEN** 系统 SHALL 分别返回 `UPSTREAM_ERROR` / `TIMEOUT`，`retryable=true`
