## Context

存量基础设施回填。原始任务卡：`docs/tasks/T03-common.md`（状态 Ready，依赖 T01，后置 T04）。本模块 `agentic-common` 给所有 adapter / monitoring / mcp 模块提供横切能力：错误码、统一异常、错误响应 DTO、Resilience4j 编排器、SDK 异常映射器、AK/SK 健康检查器。本文承载架构决策、依赖方向、SDK 类/方法细节与 AI 易错点——这些重契约不进 delta spec（本变更无 spec，纯基础设施）。

## Goals / Non-Goals

**Goals:**
- 提供统一 `ErrorCode` / `SmartomException` 体系，让上游错误语义与 `retryable` 判定在全工程一致。
- 用 `HuaweiCloudInvocation` 把"SDK 调用 + 限流 + 重试 + 异常映射 + 日志"收敛到一处，使 adapter 调用代码简洁。
- 启动期校验 AK/SK，缺失时 readiness 失败，避免 Pod 带病上线。
- 把 `HuaweiCloudProperties` 下沉到 `common`，让最底层模块拥有该配置。

**Non-Goals:**
- 任何具体 adapter 实现（T04）。
- 任何具体 MCP tool（T05）。
- 运行期 AK/SK 有效性探活（仅校验非空，不发起鉴权请求）。

## Decisions

### 错误码与异常体系

- `ErrorCode` 枚举携带 `retryable` 布尔属性，是错误是否可重试的唯一真相来源：

  | ErrorCode | retryable |
  |---|---|
  | `INVALID_PARAM` | false |
  | `UPSTREAM_THROTTLED` | true |
  | `UPSTREAM_AUTH_FAILED` | false |
  | `UPSTREAM_ERROR` | true |
  | `TIMEOUT` | true |
  | `INTERNAL` | false |

- `ErrorResponse(errorCode, errorMessage, upstreamTraceId, retryable)` 为 record，`ErrorResponse.of(code, message, upstreamTraceId)` 自动从 `code.isRetryable()` 填 `retryable`，供 MCP tool error 返回。
- `SmartomException extends RuntimeException`，字段 `errorCode` 与可空 `upstreamTraceId`。子类 `InvalidParamException`（固定 `INVALID_PARAM`）、`UpstreamException`（接受任意 `errorCode`，携带 `upstreamTraceId`）。

### SDK 异常映射（SdkExceptionMapper）

- **SDK 类**：`com.huaweicloud.sdk.core.exception.ServiceResponseException`（及其子类）。
- 映射依据 `getHttpStatusCode()`：

  | HTTP status | ErrorCode |
  |---|---|
  | 429 | `UPSTREAM_THROTTLED` |
  | 401 / 403 | `UPSTREAM_AUTH_FAILED` |
  | 500 / 502 / 503 / 504 | `UPSTREAM_ERROR` |
  | 其他 | `UPSTREAM_ERROR` |

- `TimeoutException` 及网络超时类（`SocketTimeoutException` / `ConnectTimeoutException`，经 `isTimeoutLike()` 判定）→ `TIMEOUT`。
- 其余未知异常 → `INTERNAL`。
- `upstreamTraceId` 来自华为云 `X-Request-Id`（SDK 通常在 `ServiceResponseException` 的 `getRequestId()` / errorMsg / headers），通过 `extractTraceId(sre)` 解析，可空。

### 限流 / 重试编排（HuaweiCloudInvocation + ResilienceConfig）

- 入口 `<T> T execute(String rateLimiterName, String retryName, String api, Supplier<T> call)`：
  1. 从 `RateLimiterRegistry` / `RetryRegistry` 取实例。
  2. **Decorators 组合顺序**：`Decorators.ofSupplier(call).withRateLimiter(rl).withRetry(retry).decorate()`——**重试包在外层**，失败重试时重新走限流（先 RateLimiter 后 Retry 装饰，即 Retry 在最外层）。
  3. catch 块统一调 `exceptionMapper.map(e)` 转 `SmartomException`。Resilience4j 的 `RequestNotPermitted`（拿不到令牌）→ 映射为 `UPSTREAM_THROTTLED`。
  4. INFO 日志记录 `api` + duration + result(success/error) + `upstreamTraceId`（如能取到）。
  5. Micrometer 计数由别处注入，本类聚焦核心逻辑。
- `ResilienceConfig` 的 `RetryConfig`：用 `RetryConfig.custom().retryOnException(e -> ...)` 仅对 `UpstreamException` 的可重试 `ErrorCode`（`UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT`）重试，**不对 `InvalidParamException` 重试**。默认 3 次。
- 限流器名按调用域命名（如 `ces-readonly` / `apm-readonly`），重试器名固定 `huaweicloud-retryable`，`api` 为日志/metric 标识（如 `ces.listMetrics`）。

### 健康检查（HuaweiCloudCredentialsHealthIndicator）

- `@Component implements HealthIndicator`；`health()` 读 `HuaweiCloudProperties` 的 ak / sk，任一为空白则 `Health.down().withDetail("reason", ...)`，否则 `Health.up()`。
- Bean 名为 `huaweiCloudCredentials`（驼峰，去 `HealthIndicator` 后缀），`application.yml` 的 `management.endpoint.health.group.readiness.include` 必须包含它。

### HuaweiCloudProperties 迁移

- 从 `agentic-mcp` 迁至 `com.huawei.smartom.agentic.common.config.HuaweiCloudProperties`（`common` 为底层应拥有该配置），同步更新 mcp 模块 import。

## Risks / Trade-offs

- **SDK 字段名不确定**：`ServiceResponseException` 的 `getHttpStatusCode` / `getErrorCode` / `getRequestId` 须查华为云 SDK 源码确认，不可凭印象——traceId 提取逻辑对此敏感。
- **限流与重试组合顺序**：若顺序写反（限流包在外层），重试将不重新申请令牌，限流语义失真；以 Retry 在最外层为准。
- **重试谓词遗漏**：若 `retryOnException` 错误地对 `InvalidParamException` 重试，会放大无效请求；谓词须严格按 `ErrorCode.isRetryable()` 收敛到上游可重试码。
- **HealthIndicator 仅校验非空**：AK/SK 存在但失效时 readiness 仍 UP，运行期失败由上游异常映射兜底（`UPSTREAM_AUTH_FAILED`）。
- **迁移破坏 import**：`HuaweiCloudProperties` 换包后须全量更新 mcp 模块引用，否则编译失败。
