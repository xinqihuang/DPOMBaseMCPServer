package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartomExceptionTest {

    @Test
    @DisplayName("Full constructor preserves all fields")
    void fullConstructorPreservesFields() {
        RuntimeException cause = new RuntimeException("root");
        SmartomException ex = new SmartomException(
                ErrorCode.UPSTREAM_THROTTLED, "throttled", "trace-123", cause);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED);
        assertThat(ex.getMessage()).isEqualTo("throttled");
        assertThat(ex.getUpstreamTraceId()).isEqualTo("trace-123");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Local constructor leaves traceId and cause null")
    void localConstructor() {
        SmartomException ex = new SmartomException(ErrorCode.INTERNAL, "boom");
        assertThat(ex.getUpstreamTraceId()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Null errorCode is rejected")
    void nullErrorCodeRejected() {
        assertThatThrownBy(() -> new SmartomException(null, "msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCode");
    }

    @Test
    @DisplayName("InvalidParamException carries INVALID_PARAM code, no traceId")
    void invalidParamExceptionDefaults() {
        InvalidParamException ex = new InvalidParamException("bad input");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(ex.getUpstreamTraceId()).isNull();
        assertThat(ex.getMessage()).isEqualTo("bad input");
    }

    @Test
    @DisplayName("UpstreamException accepts any error code")
    void upstreamExceptionAcceptsAnyCode() {
        UpstreamException ex = new UpstreamException(
                ErrorCode.TIMEOUT, "timed out", "x-req-1", null);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TIMEOUT);
        assertThat(ex.getUpstreamTraceId()).isEqualTo("x-req-1");
    }
}
