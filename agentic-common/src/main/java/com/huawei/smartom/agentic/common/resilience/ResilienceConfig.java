/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.resilience;

import com.huawei.smartom.agentic.common.exception.SmartomException;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j 配置定制。
 *
 * <p>Spring Boot starter 已经从 {@code application.yml} 装配好 RateLimiter 与 Retry。本类只覆盖
 * {@code huaweicloud-retryable} 重试实例的 {@code retryOnException} 谓词，从而仅当映射器将故障
 * 判定为可重试时才进行重试。
 *
 * <p>使用 {@link PostConstruct} 而不是返回 {@link RetryRegistry} 的 {@code @Bean}，是为了避免再注册
 * 一个 {@link RetryRegistry} Bean，否则会与 Resilience4j 自动装配形成循环依赖。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class ResilienceConfig {

    private final RetryRegistry retryRegistry;

    /**
     * 构造一个新的 {@code ResilienceConfig}，将在 {@link PostConstruct} 阶段对自动装配的
     * {@link RetryRegistry} 进行定制。
     *
     * @param retryRegistry 从 {@code application.yml} 自动装配的 Resilience4j 重试注册表
     */
    public ResilienceConfig(RetryRegistry retryRegistry) {
        this.retryRegistry = retryRegistry;
    }

    /**
     * 定制 {@code huaweicloud-retryable} 重试实例，仅对 ErrorCode 标记为可重试的
     * {@link SmartomException} 进行重试。
     *
     * <p>退避参数（首次等待时长、倍率、最大尝试次数）由 {@code application.yml} 提供，
     * 本方法只替换重试谓词。
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
