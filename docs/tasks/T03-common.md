# T03 — 通用基础设施 (common 模块)

> 状态: Ready · 估时: 0.5d · 依赖: T01 · 后置: T04

## 目标

实现 `com.huawei.smartom.agentic.common` 模块，给所有 adapter / monitoring / mcp 模块提供：错误码、统一异常、DTO 基类、Resilience4j 编排器、AK/SK 健康检查器。

## 范围

**做**:
- `ErrorCode` 枚举
- `SmartomException` 及子类
- 错误响应 DTO（给 MCP tool error 用）
- Resilience4j 配置类 + 帮助类 (`HuaweiCloudInvocation` 包装器)
- SDK 异常 → ErrorCode 映射器
- AK/SK 启动校验的 `HealthIndicator`
- 单元测试

**不做**:
- 任何具体的 adapter 实现（T04）
- 任何具体的 MCP tool（T05）

## 产物清单

```
agentic-common/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/common/
    error/
      ErrorCode.java                              ← enum, 含 retryable 属性
      ErrorResponse.java                          ← record, MCP tool error 返回结构
    exception/
      SmartomException.java                      ← 业务异常基类
      InvalidParamException.java                 ← 输入校验失败
      UpstreamException.java                     ← 上游错误 (含错误码/trace id)
    resilience/
      HuaweiCloudInvocation.java                 ← RateLimiter + Retry 包装器
      ResilienceConfig.java                      ← @Configuration
    sdk/
      SdkExceptionMapper.java                    ← SDK 异常 → ErrorCode
    health/
      HuaweiCloudCredentialsHealthIndicator.java ← AK/SK 校验
  src/test/java/com/huawei/smartom/agentic/common/
    error/ErrorCodeTest.java
    exception/SmartomExceptionTest.java
    resilience/HuaweiCloudInvocationTest.java
    sdk/SdkExceptionMapperTest.java
    health/HuaweiCloudCredentialsHealthIndicatorTest.java
```

## 关键技术要求

### ErrorCode

```java
public enum ErrorCode {
    INVALID_PARAM(false),
    UPSTREAM_THROTTLED(true),
    UPSTREAM_AUTH_FAILED(false),
    UPSTREAM_ERROR(true),
    TIMEOUT(true),
    INTERNAL(false);

    private final boolean retryable;

    ErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
```

### ErrorResponse (record)

```java
public record ErrorResponse(
        String errorCode,
        String errorMessage,
        String upstreamTraceId,
        boolean retryable
) {
    public static ErrorResponse of(ErrorCode code, String message, String upstreamTraceId) {
        return new ErrorResponse(code.name(), message, upstreamTraceId, code.isRetryable());
    }
}
```

### SmartomException

```java
public class SmartomException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String upstreamTraceId;  // nullable

    public SmartomException(ErrorCode errorCode, String message, String upstreamTraceId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.upstreamTraceId = upstreamTraceId;
    }
    // 各种便利构造 + getter
}
```

子类 `InvalidParamException`、`UpstreamException` 只是固定 errorCode 的便利包装。

### HuaweiCloudInvocation

核心思想：统一封装"调华为云 SDK + 限流 + 重试 + 异常映射 + 日志"，让 adapter 层调用代码简洁：

```java
@Component
public class HuaweiCloudInvocation {
    private static final Logger log = LoggerFactory.getLogger(HuaweiCloudInvocation.class);

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;
    private final SdkExceptionMapper exceptionMapper;

    public HuaweiCloudInvocation(...) { ... }

    /**
     * 执行一次华为云 SDK 调用，应用限流、重试、异常映射。
     *
     * @param rateLimiterName 限流器名 (如 "ces-readonly")
     * @param retryName       重试器名 (固定 "huaweicloud-retryable")
     * @param api             用于日志/metric 的 API 标识 (如 "ces.listMetrics")
     * @param call            SDK 调用 lambda
     * @return SDK 返回值
     * @throws SmartomException 映射后的统一异常
     */
    public <T> T execute(String rateLimiterName, String retryName, String api, Supplier<T> call) {
        // 实现:
        // 1. 取 RateLimiter / Retry 实例
        // 2. 用 Decorators 包装 call
        // 3. 在 catch 块里调 exceptionMapper.map(e)
        // 4. log INFO 记录 api + duration + result(success/error) + upstreamTraceId (如能拿到)
        // 5. Micrometer 计数 (注: counter 由别处注入，这里聚焦核心逻辑)
        ...
    }
}
```

### SdkExceptionMapper

把华为云 SDK 抛出的 `com.huaweicloud.sdk.core.exception.ServiceResponseException` 等异常映射为 `SmartomException`：

```java
@Component
public class SdkExceptionMapper {

    public SmartomException map(Throwable e) {
        if (e instanceof ServiceResponseException sre) {
            int status = sre.getHttpStatusCode();
            String upstreamTraceId = extractTraceId(sre);  // 从 headers 或 errorMsg 解析
            return switch (status) {
                case 429 -> new UpstreamException(ErrorCode.UPSTREAM_THROTTLED, sre.getMessage(), upstreamTraceId, sre);
                case 401, 403 -> new UpstreamException(ErrorCode.UPSTREAM_AUTH_FAILED, sre.getMessage(), upstreamTraceId, sre);
                case 500, 502, 503, 504 -> new UpstreamException(ErrorCode.UPSTREAM_ERROR, sre.getMessage(), upstreamTraceId, sre);
                default -> new UpstreamException(ErrorCode.UPSTREAM_ERROR, sre.getMessage(), upstreamTraceId, sre);
            };
        }
        if (e instanceof TimeoutException || isTimeoutLike(e)) {
            return new UpstreamException(ErrorCode.TIMEOUT, e.getMessage(), null, e);
        }
        return new SmartomException(ErrorCode.INTERNAL, e.getMessage(), null, e);
    }

    private String extractTraceId(ServiceResponseException sre) {
        // 华为云返回 X-Request-Id header; SDK 通常放在 errorMsg 或 reqId 字段
        // 实现时查 SDK 实际字段名
        return null;
    }

    private boolean isTimeoutLike(Throwable e) {
        // 检查 SocketTimeoutException / ConnectTimeoutException 等
        ...
    }
}
```

### HuaweiCloudCredentialsHealthIndicator

启动期校验 AK/SK 非空：

```java
@Component
public class HuaweiCloudCredentialsHealthIndicator implements HealthIndicator {

    private final HuaweiCloudProperties properties;

    @Override
    public Health health() {
        if (isBlank(properties.getAk()) || isBlank(properties.getSk())) {
            return Health.down()
                    .withDetail("reason", "HUAWEICLOUD_AK or HUAWEICLOUD_SK is missing")
                    .build();
        }
        return Health.up().build();
    }
}
```

`application.yml` 已配置把它纳入 readiness group。

注意：`HuaweiCloudProperties` 在 T01 已经定义在 `agentic-mcp` 模块。**T03 要把它迁移到 `agentic-common` 模块**，因为 `common` 是底层，应该拥有这个配置。迁移时改包名为 `com.huawei.smartom.agentic.common.config.HuaweiCloudProperties`。

## 单元测试要求

### ErrorCodeTest
- 每个枚举值的 `retryable` 属性符合预期

### SmartomExceptionTest
- 构造 + getter 正确
- `InvalidParamException` 自动设 errorCode=INVALID_PARAM
- `UpstreamException` 接受任意 errorCode

### SdkExceptionMapperTest
- 各 HTTP status code 映射到正确的 ErrorCode（429/401/403/500/502/503/504/其他）
- 非 ServiceResponseException 的网络异常映射到 TIMEOUT
- 其他异常映射到 INTERNAL
- traceId 提取正确（用 mock ServiceResponseException）

### HuaweiCloudInvocationTest
- 成功调用：返回值正确，限流器被消耗一次
- SDK 抛 429：触发重试，3 次后失败抛 UpstreamException(UPSTREAM_THROTTLED)
- SDK 抛 401：不重试，立即抛 UpstreamException(UPSTREAM_AUTH_FAILED)
- SDK 抛 5xx：重试 3 次后失败
- 限流触发：拿不到令牌时抛 `RequestNotPermitted` → 映射为 UPSTREAM_THROTTLED

### HuaweiCloudCredentialsHealthIndicatorTest
- AK 为 null → DOWN
- SK 为空字符串 → DOWN
- AK/SK 都有值 → UP

## 验收标准

- [ ] `mvn test -pl agentic-common` 全绿
- [ ] Checkstyle 0 violations
- [ ] 所有 public method 有 Javadoc
- [ ] `mvn install` 后 `agentic-mcp` 模块依赖 common，启动时 health 包含 `huaweiCloudCredentials` 项
- [ ] Pod 缺失 AK/SK 时 readiness 失败

## AI 易错点提醒

1. **`ServiceResponseException` 的字段名**：华为云 SDK 这个类的具体方法（`getHttpStatusCode` / `getErrorCode` / `getRequestId`）请查 SDK 源码确认，不要凭印象。
2. **限流和重试的组合顺序**：Resilience4j 的 Decorators 用法是 `Decorators.ofSupplier(supplier).withRateLimiter(rl).withRetry(retry).decorate()`。**重试包在外层**，失败重试时重新走限流。
3. **Retry 的 `retryOnException`**：要配置只对 UpstreamException 的特定 ErrorCode 重试，不要对 InvalidParamException 重试。在 `ResilienceConfig` 里用 `RetryConfig.custom().retryOnException(e -> ...)` 配置。
4. **HealthIndicator 必须在 Spring context 中**：`@Component` 注解，并且 application.yml 里 `management.endpoint.health.group.readiness.include` 包含它的 bean name（驼峰，去掉 HealthIndicator 后缀，即 `huaweiCloudCredentials`）。
5. **`HuaweiCloudProperties` 迁移**：从 mcp 模块挪到 common 模块时，要更新 mcp 模块的 import。

## 完成后

PR：`feat(T03): common infrastructure - errors, exceptions, resilience, health`。
