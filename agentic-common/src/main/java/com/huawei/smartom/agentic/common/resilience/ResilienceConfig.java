/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.common.resilience;

import com.huawei.smartom.agentic.common.exception.SmartomException;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j configuration adjustments.
 *
 * <p>The Spring Boot starter already wires RateLimiters and Retries from
 * {@code application.yml}. This class only overrides the {@code retryOnException}
 * predicate for the {@code huaweicloud-retryable} retry instance, so that we only
 * retry when our mapper has classified the failure as retryable.
 *
 * <p>Using {@link PostConstruct} instead of a {@code @Bean} returning {@link RetryRegistry}
 * avoids registering a second {@link RetryRegistry} bean which would cause a circular
 * dependency with the Resilience4j auto-configuration.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class ResilienceConfig {

    private final RetryRegistry retryRegistry;

    /**
     * Constructs a new {@code ResilienceConfig} that will customize the auto-configured
     * {@link RetryRegistry} during {@link PostConstruct}.
     *
     * @param retryRegistry the Resilience4j retry registry auto-configured from {@code application.yml}
     */
    public ResilienceConfig(RetryRegistry retryRegistry) {
        this.retryRegistry = retryRegistry;
    }

    /**
     * Customize the {@code huaweicloud-retryable} Retry instance so it only retries
     * {@link SmartomException}s whose ErrorCode is marked retryable.
     *
     * <p>Backoff durations (initial wait, multiplier, max attempts) come from
     * {@code application.yml}. We replace only the retry predicate here.
     */
    @PostConstruct
    public void customizeHuaweicloudRetryable() {
        RetryConfig config = RetryConfig.from(retryRegistry.getConfiguration("huaweicloud-retryable")
                        .orElse(retryRegistry.getDefaultConfig()))
                .retryOnException(throwable -> {
                    if (throwable instanceof SmartomException smartom) {
                        return smartom.getErrorCode().isRetryable();
                    }
                    // Non-SmartomException means the mapper hasn't run yet;
                    // be conservative and let it through unretried.
                    return false;
                })
                .build();
        retryRegistry.replace("huaweicloud-retryable", Retry.of("huaweicloud-retryable", config));
    }
}
