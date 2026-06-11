/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码枚举的单元测试。
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

    @Test
    @DisplayName("every error code carries a non-blank agent-facing hint")
    void everyCodeHasHint() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getHint())
                    .as("hint of %s", code)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("ErrorResponse.of populates hint from the error code")
    void errorResponsePopulatesHint() {
        ErrorResponse resp = ErrorResponse.of(ErrorCode.UPSTREAM_THROTTLED, "throttled", "trace-1");

        assertThat(resp.hint()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.getHint());
        assertThat(resp.retryable()).isTrue();
        assertThat(resp.upstreamTraceId()).isEqualTo("trace-1");
    }
}
