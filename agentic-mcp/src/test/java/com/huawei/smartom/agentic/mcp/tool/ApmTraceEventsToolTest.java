/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ApmTraceEventsTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmTraceEventsToolTest {

    private ApmTraceService service;
    private ApmTraceEventsTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmTraceService.class);
        tool = new ApmTraceEventsTool(service);
    }

    @Test
    @DisplayName("Success: service response passed through unchanged")
    void successPassthrough() {
        ApmTraceEventsResponse expected = new ApmTraceEventsResponse(List.of());
        when(service.showTraceEvents("trace-abc")).thenReturn(expected);

        assertThat(tool.showTraceEvents("trace-abc")).isSameAs(expected);
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse")
    void invalidParamConverted() {
        when(service.showTraceEvents(anyString()))
                .thenThrow(new InvalidParamException("trace_id is required"));

        Object result = tool.showTraceEvents(" ");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("trace_id");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.showTraceEvents(anyString())).thenThrow(new UpstreamException(
                ErrorCode.UPSTREAM_ERROR, "server error", "req-apm-events", null));

        Object result = tool.showTraceEvents("trace-abc");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-apm-events");
    }
}
