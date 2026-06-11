/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.UpstreamException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolCallSupport} 的单元测试：验证成功透传与两类异常到
 * {@link ErrorResponse} 的统一转换。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ToolCallSupportTest {

    @Test
    @DisplayName("success result is passed through unchanged")
    void successPassthrough() {
        Object payload = new Object();

        Object result = ToolCallSupport.execute("dummy_tool", () -> payload);

        assertThat(result).isSameAs(payload);
    }

    @Test
    @DisplayName("SmartomException is converted to a structured ErrorResponse with hint")
    void smartomExceptionConverted() {
        Object result = ToolCallSupport.execute("dummy_tool", () -> {
            throw new UpstreamException(ErrorCode.UPSTREAM_THROTTLED, "throttled by CES", "trace-42", null);
        });

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.errorMessage()).isEqualTo("throttled by CES");
        assertThat(err.upstreamTraceId()).isEqualTo("trace-42");
        assertThat(err.retryable()).isTrue();
        assertThat(err.hint()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.getHint());
    }

    @Test
    @DisplayName("unexpected RuntimeException is converted to INTERNAL, not propagated")
    void runtimeExceptionConvertedToInternal() {
        Object result = ToolCallSupport.execute("dummy_tool", () -> {
            throw new IllegalStateException("mapper blew up");
        });

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INTERNAL.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.hint()).isEqualTo(ErrorCode.INTERNAL.getHint());
        assertThat(err.errorMessage())
                .contains("IllegalStateException")
                .contains("mapper blew up")
                .doesNotContain("at com.huawei");
    }

    @Test
    @DisplayName("RuntimeException without message still yields a readable INTERNAL message")
    void runtimeExceptionWithoutMessage() {
        Object result = ToolCallSupport.execute("dummy_tool", () -> {
            throw new NullPointerException();
        });

        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INTERNAL.name());
        assertThat(err.errorMessage()).isEqualTo("Unexpected internal error (NullPointerException)");
    }
}
