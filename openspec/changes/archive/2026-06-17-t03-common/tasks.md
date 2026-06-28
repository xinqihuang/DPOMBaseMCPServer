> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T03-common.md`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. 错误码与异常体系（error / exception）

- [x] 1.1 `ErrorCode` 枚举（`INVALID_PARAM` / `UPSTREAM_THROTTLED` / `UPSTREAM_AUTH_FAILED` / `UPSTREAM_ERROR` / `TIMEOUT` / `INTERNAL`，含 `retryable` 属性）
- [x] 1.2 `ErrorResponse` record + `of(ErrorCode, message, upstreamTraceId)` 工厂
- [x] 1.3 `SmartomException` 基类（`errorCode` + 可空 `upstreamTraceId`）
- [x] 1.4 子类 `InvalidParamException`（固定 `INVALID_PARAM`）/ `UpstreamException`（任意 errorCode）

## 2. 限流 / 重试编排（resilience）

- [x] 2.1 `ResilienceConfig`（RateLimiter / Retry 注册表，`retryOnException` 仅对可重试 ErrorCode）
- [x] 2.2 `HuaweiCloudInvocation.execute(rateLimiterName, retryName, api, call)`，Retry 包在 RateLimiter 外层
- [x] 2.3 `RequestNotPermitted` → `UPSTREAM_THROTTLED`，INFO 日志含 api / duration / result / upstreamTraceId

## 3. SDK 异常映射（sdk）

- [x] 3.1 `SdkExceptionMapper.map(Throwable)`：429→THROTTLED，401/403→AUTH_FAILED，5xx/其他→UPSTREAM_ERROR
- [x] 3.2 超时类异常 → `TIMEOUT`；未知异常 → `INTERNAL`
- [x] 3.3 `extractTraceId` 从 `ServiceResponseException`（X-Request-Id）解析 upstreamTraceId（可空）

## 4. 健康检查与配置（health / config）

- [x] 4.1 `HuaweiCloudCredentialsHealthIndicator`：AK/SK 空白 → DOWN，否则 UP
- [x] 4.2 `HuaweiCloudProperties` 从 `agentic-mcp` 迁移至 `agentic-common.config`，更新 mcp import
- [x] 4.3 `application.yml` 将 `huaweiCloudCredentials` 纳入 readiness group

## 5. 测试

- [x] 5.1 `ErrorCodeTest`（各枚举 retryable 断言）
- [x] 5.2 `SmartomExceptionTest`（构造 / getter / 子类 errorCode）
- [x] 5.3 `SdkExceptionMapperTest`（429/401/403/5xx/其他/超时/未知 映射 + traceId 提取）
- [x] 5.4 `HuaweiCloudInvocationTest`（成功 / 429 重试 / 401 不重试 / 5xx 重试 / 限流触发）
- [x] 5.5 `HuaweiCloudCredentialsHealthIndicatorTest`（AK null / SK 空串 / 都有值）

## 6. 遗留项（本期未交付）

- [ ] 6.1 任何具体 adapter 实现（T04）
- [ ] 6.2 任何具体 MCP tool（T05）
