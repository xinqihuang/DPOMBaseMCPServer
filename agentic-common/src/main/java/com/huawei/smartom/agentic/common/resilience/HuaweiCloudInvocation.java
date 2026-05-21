package com.huawei.smartom.agentic.common.resilience;

import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Unified wrapper for Huawei Cloud SDK calls.
 *
 * <p>Applies in order: rate limiting -&gt; SDK call -&gt; exception mapping (to SmartomException)
 * -&gt; retry (only on retryable {@link com.huawei.smartom.agentic.common.error.ErrorCode}s).
 *
 * <p>Logs the API identifier, duration and final result (success or error code) on every call.
 */
@Component
public class HuaweiCloudInvocation {

    private static final Logger LOG = LoggerFactory.getLogger(HuaweiCloudInvocation.class);

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;
    private final SdkExceptionMapper exceptionMapper;

    public HuaweiCloudInvocation(
            RateLimiterRegistry rateLimiterRegistry,
            RetryRegistry retryRegistry,
            SdkExceptionMapper exceptionMapper) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.retryRegistry = retryRegistry;
        this.exceptionMapper = exceptionMapper;
    }

    /**
     * Execute an SDK call under rate-limiting + retry + exception mapping.
     *
     * @param rateLimiterName  rate limiter instance name (e.g. {@code "ces-readonly"})
     * @param retryName        retry instance name (typically {@code "huaweicloud-retryable"})
     * @param api              free-form API id for logging/metrics (e.g. {@code "ces.listMetrics"})
     * @param call             the actual SDK invocation
     * @param <T>              SDK response type
     * @return the SDK response
     * @throws SmartomException with a mapped {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    public <T> T execute(String rateLimiterName, String retryName, String api, Supplier<T> call) {
        if (rateLimiterName == null || retryName == null || api == null || call == null) {
            throw new IllegalArgumentException("rateLimiterName / retryName / api / call must not be null");
        }
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName);
        Retry retry = retryRegistry.retry(retryName);
        long start = System.currentTimeMillis();
        try {
            T result = Decorators.ofSupplier(() -> invokeAndMap(call))
                    .withRateLimiter(rateLimiter)
                    .withRetry(retry)
                    .decorate()
                    .get();
            LOG.info("Huawei Cloud SDK call success, api={}, durationMs={}",
                    api, System.currentTimeMillis() - start);
            return result;
        } catch (SmartomException e) {
            LOG.warn("Huawei Cloud SDK call failed, api={}, durationMs={}, errorCode={}, upstreamTraceId={}",
                    api,
                    System.currentTimeMillis() - start,
                    e.getErrorCode(),
                    e.getUpstreamTraceId());
            throw e;
        } catch (RuntimeException e) {
            // Defensive: any unexpected exception from the decorator chain
            SmartomException mapped = exceptionMapper.map(e);
            LOG.warn("Huawei Cloud SDK call failed (unexpected), api={}, durationMs={}, errorCode={}",
                    api,
                    System.currentTimeMillis() - start,
                    mapped.getErrorCode());
            throw mapped;
        }
    }

    private <T> T invokeAndMap(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            // Map BEFORE the retry layer sees it, so retryOnException predicate works on SmartomException.
            throw exceptionMapper.map(e);
        }
    }
}
