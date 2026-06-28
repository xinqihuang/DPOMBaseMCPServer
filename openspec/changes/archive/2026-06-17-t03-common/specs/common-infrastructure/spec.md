## ADDED Requirements

### Requirement: 统一错误码与可重试判定

系统 SHALL 提供 `ErrorCode` 枚举作为错误语义与 `retryable` 判定的唯一真相来源，并提供 `ErrorResponse` 作为 MCP tool error 的结构化返回。`ErrorResponse` MUST 从 `ErrorCode.isRetryable()` 推导 `retryable`，不得手工传入不一致的值。

#### Scenario: 各错误码的可重试属性
- **WHEN** 读取 `ErrorCode` 各枚举值的 `retryable`
- **THEN** `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` SHALL 为 `true`
- **AND** `INVALID_PARAM` / `UPSTREAM_AUTH_FAILED` / `INTERNAL` SHALL 为 `false`

#### Scenario: 错误响应推导 retryable
- **WHEN** 调用 `ErrorResponse.of(code, message, upstreamTraceId)`
- **THEN** 返回的 `retryable` SHALL 等于 `code.isRetryable()`
- **AND** `errorCode` SHALL 为 `code.name()`

### Requirement: 统一业务异常

系统 SHALL 提供 `SmartomException` 基类，携带 `errorCode` 与可空 `upstreamTraceId`。`InvalidParamException` MUST 固定 `errorCode=INVALID_PARAM`；`UpstreamException` MUST 接受任意 `errorCode` 并携带可空 `upstreamTraceId`。

#### Scenario: 基类构造与读取
- **WHEN** 以 errorCode / message / upstreamTraceId / cause 构造 `SmartomException`
- **THEN** getter SHALL 原样返回 errorCode 与 upstreamTraceId
- **AND** message 与 cause SHALL 透传到 `RuntimeException`

#### Scenario: 子类错误码语义
- **WHEN** 构造 `InvalidParamException`
- **THEN** 其 `errorCode` SHALL 为 `INVALID_PARAM`
- **AND** `UpstreamException` SHALL 允许传入任意 `ErrorCode`

### Requirement: SDK 异常到统一错误码的映射

系统 SHALL 通过 `SdkExceptionMapper.map(Throwable)` 将华为云 SDK 异常映射为 `SmartomException`，不让 SDK 异常透传到上层；映射后异常 SHALL 携带可空 `upstreamTraceId`（华为云 `X-Request-Id`）。

#### Scenario: HTTP 状态码映射
- **WHEN** 传入 `ServiceResponseException` 且 `getHttpStatusCode()` 为 429
- **THEN** 映射结果 SHALL 为 `UPSTREAM_THROTTLED`
- **AND** 401/403 SHALL 映射为 `UPSTREAM_AUTH_FAILED`，500/502/503/504 及其他 SHALL 映射为 `UPSTREAM_ERROR`

#### Scenario: 超时与未知异常映射
- **WHEN** 传入超时类异常（`TimeoutException` / `SocketTimeoutException` / `ConnectTimeoutException`）
- **THEN** 映射结果 SHALL 为 `TIMEOUT`
- **AND** 其余未知异常 SHALL 映射为 `INTERNAL`

### Requirement: 限流与重试编排

系统 SHALL 提供 `HuaweiCloudInvocation.execute(rateLimiterName, retryName, api, call)`，统一应用限流、重试与异常映射。重试 MUST 包在限流外层（失败重试时重新申请令牌），且 MUST 仅对可重试的 `ErrorCode`（`UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT`）重试，不得对 `InvalidParamException` 重试。

#### Scenario: 成功调用消耗一次令牌
- **WHEN** `call` 正常返回
- **THEN** 系统 SHALL 返回其结果
- **AND** 对应 RateLimiter SHALL 被消耗一次

#### Scenario: 可重试上游错误触发重试
- **WHEN** 上游返回 429（或 5xx）
- **THEN** 系统 SHALL 触发重试，重试耗尽后抛出 `UpstreamException(UPSTREAM_THROTTLED)`（或 `UPSTREAM_ERROR`）

#### Scenario: 不可重试错误不重试
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 立即抛出 `UpstreamException(UPSTREAM_AUTH_FAILED)`，不重试

#### Scenario: 限流拒绝映射
- **WHEN** RateLimiter 拿不到令牌抛出 `RequestNotPermitted`
- **THEN** 系统 SHALL 映射为 `UPSTREAM_THROTTLED`

### Requirement: AK/SK 启动期健康检查

系统 SHALL 提供 `HuaweiCloudCredentialsHealthIndicator`，在 readiness group 中校验 `HuaweiCloudProperties` 的 ak / sk 非空白。AK 或 SK 缺失时 MUST 报告 DOWN 并附带原因。

#### Scenario: 凭据缺失时 DOWN
- **WHEN** ak 为 null 或 sk 为空白字符串
- **THEN** 健康状态 SHALL 为 DOWN
- **AND** SHALL 附带 `reason` 说明缺失项

#### Scenario: 凭据齐全时 UP
- **WHEN** ak 与 sk 均非空白
- **THEN** 健康状态 SHALL 为 UP
