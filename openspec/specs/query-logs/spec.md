# query-logs Specification

## Purpose
查询 AOM 应用/节点/自定义日志（时间窗 + 关键字，支持通配与布尔组合），定位错误日志行与堆栈。
## Requirements
### Requirement: AOM 日志检索

系统 SHALL 提供只读工具 `query_logs`，调用 AOM SDK `listLogItems`（`type=querylogs`），按 `(category, startTime, endTime, keyWord)` 检索华为云 AOM 应用 / 主机 / 自定义日志，并返回上游响应 `{ result, errorCode, errorMessage }`。

工具 MUST 暴露 6 个参数（`category` / `startTime` / `endTime` / `keyWord` / `pageSize` / `isDesc`），其中 `keyWord` / `pageSize` / `isDesc` 为可选（`@ToolParam(required = false)`）。该工具 MUST 为只读（`readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`），不做日志写入 / 日志组配置，不做翻页与 `lineNum` 游标管理，不跨 region / projectId。

#### Scenario: 按合法参数检索日志
- **WHEN** Agent 传入合法的 `category` 与时间窗（可选 `keyWord` / `pageSize` / `isDesc`）
- **THEN** 系统 SHALL 装配 SDK `ListLogItemsRequest`（`withType("querylogs")`），按字段映射注入 `QueryBodyParam`
- **AND** 返回上游 `result` / `errorCode` / `errorMessage`

#### Scenario: 关键字语法透传
- **GIVEN** `keyWord` 含上游语法（精确 / 通配符 `*ERR*` / 短语 / `&&` / `||`）
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样透传到 SDK，不做本地改写或转义

### Requirement: result 字段 String 透传

系统 SHALL 将上游 `result` 字段以 String 形式原样透传，MUST NOT 在 adapter 层对其做 JSON 解析（`ObjectMapper.readTree` 等），以避免随上游 `result` 内部 schema 升级而断裂。Tool description MUST 明确告知 "Returns the raw upstream 'result' JSON string for downstream parsing"。

#### Scenario: result 不解析透传
- **GIVEN** 上游 `listLogItems` 返回 `result`（其本身为一段 JSON 字符串）
- **WHEN** adapter 映射为 `AomQueryLogsResponse`
- **THEN** 系统 SHALL 将 `result` 以 String 形式原样放入 DTO
- **AND** MUST NOT 在 adapter 层解析其内部结构

### Requirement: 时间参数为 UTC 毫秒

系统 SHALL 约定 `startTime` / `endTime` 为 UTC 毫秒（`long`）。`endTime` MUST 严格大于 `startTime`。系统 MUST NOT 对时间参数做秒 / ISO8601 / `OffsetDateTime` 的隐式转换。

#### Scenario: endTime 不大于 startTime
- **WHEN** `endTime` <= `startTime`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: 输入校验

系统 SHALL 在 service 层执行 5 项校验：`request` 非 null；`category` ∈ `{app_log, node_log, custom_log}`；`startTime` / `endTime` 任一非 null；`endTime` 严格大于 `startTime`；`pageSize` ∈ [1, 1000]。`AomQueryLogsRequest` 紧凑构造 MUST 仅设默认值（`pageSize` 默认 100，`isDesc` 默认 true）而 MUST NOT 抛异常或做业务校验。校验失败 SHALL 走 `InvalidParamException` → `INVALID_PARAM`。

#### Scenario: category 不在白名单
- **WHEN** `category` 不在 `{app_log, node_log, custom_log}`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，消息含传入取值

#### Scenario: 时间参数缺失
- **WHEN** `startTime` 或 `endTime` 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: pageSize 越界
- **WHEN** `pageSize` < 1 或 > 1000（Tool 端显式传 0 / 1001）
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 紧凑构造仅设默认
- **GIVEN** `pageSize` 为 null 且 `isDesc` 为 null
- **WHEN** 构造 `AomQueryLogsRequest`
- **THEN** 系统 SHALL 将 `pageSize` 设为 100、`isDesc` 设为 true
- **AND** MUST NOT 抛出异常

### Requirement: SDK 字段映射保真

系统 SHALL 按既定映射装配 SDK 请求：`pageSize` MUST 经 `withPageSizeSize(String.valueOf(pageSize))` 以 String 传入（SDK 字段名带双 `Size`）；`withType("querylogs")` MUST 必传；`keyWord` MUST 仅在非 null 时调用 `setKeyWord`。

#### Scenario: pageSize 以 String 装配
- **WHEN** 装配 SDK `QueryBodyParam`
- **THEN** 系统 SHALL 调用 `withPageSizeSize(String.valueOf(pageSize))`，而非传 int

#### Scenario: keyWord 为 null 时不装配
- **GIVEN** `keyWord` 为 null
- **WHEN** 装配 SDK 请求
- **THEN** 系统 SHALL NOT 调用 `setKeyWord`

### Requirement: 上游异常映射

系统 SHALL 将 AOM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。Tool 层 MUST 仅 catch `SmartomException` 转 `ErrorResponse`，MUST NOT catch 宽泛 `Exception`。

#### Scenario: 限流映射
- **WHEN** 上游返回 429 / SDK throttling
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`，并透传 `upstream_trace_id`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 服务端错误与超时映射
- **WHEN** 上游返回 5xx 或调用超时
- **THEN** 系统 SHALL 分别返回 `UPSTREAM_ERROR` / `TIMEOUT`，`retryable=true`

