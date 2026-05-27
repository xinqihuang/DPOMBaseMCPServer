/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.sdk;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import com.huaweicloud.sdk.core.exception.ClientRequestException;
import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServerResponseException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sdk Exception Mapper Test.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class SdkExceptionMapperTest {

    private final SdkExceptionMapper mapper = new SdkExceptionMapper();

    @Test
    @DisplayName("HTTP 429 -> UPSTREAM_THROTTLED, traceId preserved")
    void http429() {
        ServiceResponseException sre = new ClientRequestException(
                429, "CES.0429", "Too Many Requests", "req-429");
        SmartomException mapped = mapper.map(sre);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED);
        assertThat(mapped.getUpstreamTraceId()).isEqualTo("req-429");
        assertThat(mapped.getCause()).isSameAs(sre);
    }

    @Test
    @DisplayName("HTTP 401 -> UPSTREAM_AUTH_FAILED, not retryable")
    void http401() {
        ServiceResponseException sre = new ClientRequestException(
                401, "APIGW.0301", "Unauthorized", "req-401");
        SmartomException mapped = mapper.map(sre);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(mapped.getErrorCode().isRetryable()).isFalse();
    }

    @Test
    @DisplayName("HTTP 403 -> UPSTREAM_AUTH_FAILED")
    void http403() {
        ServiceResponseException sre = new ClientRequestException(
                403, "APIGW.0303", "Forbidden", "req-403");
        SmartomException mapped = mapper.map(sre);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_AUTH_FAILED);
    }

    @Test
    @DisplayName("HTTP 500 -> UPSTREAM_ERROR")
    void http500() {
        ServiceResponseException sre = new ServerResponseException(
                500, "CES.5000", "Internal Server Error", "req-500");
        SmartomException mapped = mapper.map(sre);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
        assertThat(mapped.getErrorCode().isRetryable()).isTrue();
    }

    @Test
    @DisplayName("HTTP 503 -> UPSTREAM_ERROR, retryable")
    void http503() {
        ServiceResponseException sre = new ServerResponseException(
                503, "CES.5030", "Service Unavailable", "req-503");
        SmartomException mapped = mapper.map(sre);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("HTTP 400 -> UPSTREAM_ERROR (catch-all)")
    void http400() {
        ServiceResponseException sre = new ClientRequestException(
                400, "CES.0001", "Bad Request", "req-400");
        SmartomException mapped = mapper.map(sre);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("RequestTimeoutException -> TIMEOUT, no traceId")
    void requestTimeout() {
        RequestTimeoutException ex = new RequestTimeoutException("read timed out");
        SmartomException mapped = mapper.map(ex);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.TIMEOUT);
        assertThat(mapped.getUpstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("ConnectionException -> TIMEOUT")
    void connectionException() {
        ConnectionException ex = new ConnectionException("connect refused");
        SmartomException mapped = mapper.map(ex);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.TIMEOUT);
    }

    @Test
    @DisplayName("RequestNotPermitted (rate limiter) -> UPSTREAM_THROTTLED")
    void rateLimiterRejection() {
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(
                RateLimiter.ofDefaults("test"));
        SmartomException mapped = mapper.map(ex);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED);
    }

    @Test
    @DisplayName("Plain RuntimeException -> INTERNAL")
    void plainRuntime() {
        RuntimeException ex = new RuntimeException("boom");
        SmartomException mapped = mapper.map(ex);
        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.INTERNAL);
    }

    @Test
    @DisplayName("SmartomException is passed through, not double-wrapped")
    void smartomPassthrough() {
        SmartomException original = new SmartomException(
                ErrorCode.UPSTREAM_AUTH_FAILED, "msg", "req-x", null);
        SmartomException mapped = mapper.map(original);
        assertThat(mapped).isSameAs(original);
    }
}
