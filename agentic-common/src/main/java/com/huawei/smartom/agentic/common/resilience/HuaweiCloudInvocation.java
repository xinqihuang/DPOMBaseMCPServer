/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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
 * 华为云 SDK 调用的统一封装。
 *
 * <p>处理顺序为：限流 -&gt; SDK 调用 -&gt; 异常映射（转为 SmartomException）
 * -&gt; 重试（仅对可重试的 {@link com.huawei.smartom.agentic.common.error.ErrorCode} 触发）。
 *
 * <p>每次调用都会记录 API 标识、耗时以及最终结果（成功或错误码）。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class HuaweiCloudInvocation {

    private static final Logger LOG = LoggerFactory.getLogger(HuaweiCloudInvocation.class);

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;
    private final SdkExceptionMapper exceptionMapper;

    /**
     * 构造一个新的 {@code HuaweiCloudInvocation}，注入用于装饰每次华为云 SDK 调用的容错注册表
     * 和 SDK 异常映射器。
     *
     * @param rateLimiterRegistry 按服务作用域提供命名 RateLimiter 实例的注册表
     * @param retryRegistry       提供命名 Retry 实例的注册表（例如 huaweicloud-retryable）
     * @param exceptionMapper     将 SDK 异常转换为 {@link SmartomException} 的映射器
     */
    public HuaweiCloudInvocation(
            RateLimiterRegistry rateLimiterRegistry,
            RetryRegistry retryRegistry,
            SdkExceptionMapper exceptionMapper) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.retryRegistry = retryRegistry;
        this.exceptionMapper = exceptionMapper;
    }

    /**
     * 在限流、重试与异常映射的组合下执行一次 SDK 调用。
     *
     * @param rateLimiterName  限流器实例名（例如 {@code "ces-readonly"}）
     * @param retryName        重试实例名（通常为 {@code "huaweicloud-retryable"}）
     * @param api              用于日志与监控的自由格式 API 标识（例如 {@code "ces.listMetrics"}）
     * @param call             实际的 SDK 调用
     * @param <T>              SDK 响应类型
     * @return SDK 响应
     * @throws IllegalArgumentException 当任一参数为 {@code null}
     * @throws SmartomException 携带已映射的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
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
        }
        catch (SmartomException e) {
            LOG.warn("Huawei Cloud SDK call failed, api={}, durationMs={}, errorCode={}, upstreamTraceId={}",
                    api,
                    System.currentTimeMillis() - start,
                    e.getErrorCode(),
                    e.getUpstreamTraceId());
            throw e;
        }
        catch (RuntimeException e) {
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
        }
        catch (RuntimeException e) {
            // Map BEFORE the retry layer sees it, so retryOnException predicate works on SmartomException.
            throw exceptionMapper.map(e);
        }
    }
}
