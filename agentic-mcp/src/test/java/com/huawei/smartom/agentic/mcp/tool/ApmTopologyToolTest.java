/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApmTopologyTool} 单元测试。
 *
 * <p>覆盖 Tool 层成功透传以及业务 / 上游异常到 {@link ErrorResponse} 的映射。
 * Tool 层本身不做参数校验，{@code trace_id} 的非空校验由下层 {@link ApmTraceService} 负责。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class ApmTopologyToolTest {

    private static final String TRACE_ID = "trace-abc";

    private ApmTraceService service;
    private ApmTopologyTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmTraceService.class);
        tool = new ApmTopologyTool(service);
    }

    @Test
    @DisplayName("Success: request DTO carries the traceId and response is returned unchanged")
    void successPassthrough() {
        ApmGetTopologyResponse expected =
                new ApmGetTopologyResponse(TRACE_ID, List.of(), List.of());
        when(service.getTopology(any(ApmGetTopologyRequest.class))).thenReturn(expected);

        Object result = tool.getServiceTopology(TRACE_ID);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<ApmGetTopologyRequest> captor =
                ArgumentCaptor.forClass(ApmGetTopologyRequest.class);
        verify(service).getTopology(captor.capture());
        ApmGetTopologyRequest req = captor.getValue();
        assertThat(req.traceId()).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("Null traceId is passed through (service is responsible for the rejection)")
    void nullTraceIdReachesService() {
        when(service.getTopology(any(ApmGetTopologyRequest.class)))
                .thenThrow(new InvalidParamException("trace_id is required"));

        Object result = tool.getServiceTopology(null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("trace_id");

        ArgumentCaptor<ApmGetTopologyRequest> captor =
                ArgumentCaptor.forClass(ApmGetTopologyRequest.class);
        verify(service).getTopology(captor.capture());
        assertThat(captor.getValue().traceId()).isNull();
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse")
    void serviceInvalidParamConverted() {
        when(service.getTopology(any(ApmGetTopologyRequest.class)))
                .thenThrow(new InvalidParamException("trace_id is required"));

        Object result = tool.getServiceTopology("   ");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("trace_id");
    }

    @Test
    @DisplayName("UpstreamException (throttled) is converted to ErrorResponse with trace id")
    void upstreamThrottledConverted() {
        when(service.getTopology(any(ApmGetTopologyRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-topo-1", null));

        Object result = tool.getServiceTopology(TRACE_ID);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-topo-1");
    }

    @Test
    @DisplayName("UpstreamException (5xx) is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.getTopology(any(ApmGetTopologyRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "boom", "req-topo-5xx", null));

        Object result = tool.getServiceTopology(TRACE_ID);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-topo-5xx");
    }
}
