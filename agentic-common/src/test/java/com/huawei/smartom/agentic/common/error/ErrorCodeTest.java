/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error Code Test.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class ErrorCodeTest {

    @Test
    @DisplayName("INVALID_PARAM is not retryable")
    void invalidParamNotRetryable() {
        assertThat(ErrorCode.INVALID_PARAM.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("UPSTREAM_THROTTLED is retryable")
    void throttledRetryable() {
        assertThat(ErrorCode.UPSTREAM_THROTTLED.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("UPSTREAM_AUTH_FAILED is not retryable")
    void authFailedNotRetryable() {
        assertThat(ErrorCode.UPSTREAM_AUTH_FAILED.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("UPSTREAM_ERROR is retryable")
    void upstreamErrorRetryable() {
        assertThat(ErrorCode.UPSTREAM_ERROR.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("TIMEOUT is retryable")
    void timeoutRetryable() {
        assertThat(ErrorCode.TIMEOUT.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("INTERNAL is not retryable")
    void internalNotRetryable() {
        assertThat(ErrorCode.INTERNAL.isRetryable()).isFalse();
    }
}
