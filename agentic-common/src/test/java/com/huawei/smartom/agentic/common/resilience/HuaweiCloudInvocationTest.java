/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.resilience;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.core.exception.ClientRequestException;
import com.huaweicloud.sdk.core.exception.ServerResponseException;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Huawei Cloud Invocation Test.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class HuaweiCloudInvocationTest {

    private static final String RL = "ces-readonly";
    private static final String RETRY = "huaweicloud-retryable";
    private static final String API = "ces.listMetrics";

    private RateLimiterRegistry rateLimiterRegistry;
    private RetryRegistry retryRegistry;
    private SdkExceptionMapper mapper;
    private HuaweiCloudInvocation invocation;

    @BeforeEach
    void setUp() {
        rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rateLimiterRegistry.rateLimiter(RL);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException smartomEx && smartomEx.getErrorCode().isRetryable())
                .build();
        retryRegistry = RetryRegistry.of(retryConfig);
        retryRegistry.retry(RETRY);

        mapper = new SdkExceptionMapper();
        invocation = new HuaweiCloudInvocation(rateLimiterRegistry, retryRegistry, mapper);
    }

    @Test
    @DisplayName("Successful call returns SDK result without retry")
    void successfulCall() {
        String result = invocation.execute(RL, RETRY, API, () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("HTTP 429 triggers retry; after exhausting attempts, throws UPSTREAM_THROTTLED")
    void retryOnThrottled() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> invocation.execute(RL, RETRY, API, () -> {
            attempts.incrementAndGet();
            throw new ClientRequestException(429, "CES.0429", "throttled", "req-429");
        }))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("HTTP 401 does NOT trigger retry; fails fast")
    void noRetryOnAuthFailed() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> invocation.execute(RL, RETRY, API, () -> {
            attempts.incrementAndGet();
            throw new ClientRequestException(401, "APIGW.0301", "unauthorized", "req-401");
        }))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP 5xx triggers retry, fails after exhaustion with UPSTREAM_ERROR")
    void retryOn5xx() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> invocation.execute(RL, RETRY, API, () -> {
            attempts.incrementAndGet();
            throw new ServerResponseException(503, "CES.5030", "unavailable", "req-503");
        }))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_ERROR);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Transient failure then success: retry recovers and returns result")
    void retryRecovers() {
        AtomicInteger attempts = new AtomicInteger();
        String result = invocation.execute(RL, RETRY, API, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new ServerResponseException(503, "CES.5030", "transient", "req-503");
            }
            return "recovered";
        });
        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rate-limiter rejection (no permits) -> UPSTREAM_THROTTLED")
    void rateLimiterRejection() {
        RateLimiterRegistry zeroPermits = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(10))
                .timeoutDuration(Duration.ZERO)
                .build());
        zeroPermits.rateLimiter(RL);
        HuaweiCloudInvocation tightInvocation =
                new HuaweiCloudInvocation(zeroPermits, retryRegistry, mapper);

        // First call consumes the only permit
        tightInvocation.execute(RL, RETRY, API, () -> "first");

        // Second call should be rejected by the rate limiter
        assertThatThrownBy(() -> tightInvocation.execute(RL, RETRY, API, () -> "blocked"))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED);
    }

    @Test
    @DisplayName("Null arguments are rejected")
    void nullArgsRejected() {
        assertThatThrownBy(() -> invocation.execute(null, RETRY, API, () -> "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
