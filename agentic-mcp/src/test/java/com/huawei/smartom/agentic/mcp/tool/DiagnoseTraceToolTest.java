/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.DiagnoseTraceRequest;
import com.huawei.smartom.agentic.monitoring.apm.DiagnoseTraceResponse;
import com.huawei.smartom.agentic.monitoring.apm.DiagnoseTraceService;

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
 * {@link DiagnoseTraceTool} 单元测试。
 *
 * <p>Tool 层很薄：构造 {@link DiagnoseTraceRequest}、透传服务结果、把 {@code SmartomException}
 * 转 {@link ErrorResponse}。编排与校验逻辑下沉到 {@link DiagnoseTraceService}。覆盖：
 * <ul>
 *   <li>入参完整透传到服务层</li>
 *   <li>可选参数为 null 时原样透传</li>
 *   <li>{@link InvalidParamException} 转 {@link ErrorResponse}</li>
 *   <li>{@link UpstreamException} 转 {@link ErrorResponse} 并携带 upstream trace id</li>
 * </ul>
 *
 * @author huangxinqi
 * @since 2026-06-18
 */
class DiagnoseTraceToolTest {

    private static final String TRACE_ID = "trace-abc";
    private static final Long BUSINESS_ID = 7000L;
    private static final Integer MAX_SUSPECTS = 3;

    private DiagnoseTraceService service;
    private DiagnoseTraceTool tool;

    @BeforeEach
    void setUp() {
        service = mock(DiagnoseTraceService.class);
        tool = new DiagnoseTraceTool(service);
    }

    @Test
    @DisplayName("Success: all tool params are forwarded into the request DTO unchanged")
    void successPassthrough() {
        DiagnoseTraceResponse expected = new DiagnoseTraceResponse(
                TRACE_ID, null, 0, 0, List.of(), List.of());
        when(service.diagnose(any(DiagnoseTraceRequest.class))).thenReturn(expected);

        Object result = tool.diagnoseTrace(TRACE_ID, BUSINESS_ID, MAX_SUSPECTS, Boolean.FALSE);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<DiagnoseTraceRequest> captor =
                ArgumentCaptor.forClass(DiagnoseTraceRequest.class);
        verify(service).diagnose(captor.capture());
        DiagnoseTraceRequest req = captor.getValue();
        assertThat(req.traceId()).isEqualTo(TRACE_ID);
        assertThat(req.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(req.maxSuspectEvents()).isEqualTo(MAX_SUSPECTS);
        assertThat(req.includeSlowest()).isFalse();
    }

    @Test
    @DisplayName("Success: null optional params propagate as null")
    void successWithNullOptionals() {
        DiagnoseTraceResponse expected = new DiagnoseTraceResponse(
                TRACE_ID, null, 1, 1, List.of(), List.of());
        when(service.diagnose(any(DiagnoseTraceRequest.class))).thenReturn(expected);

        Object result = tool.diagnoseTrace(TRACE_ID, null, null, null);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<DiagnoseTraceRequest> captor =
                ArgumentCaptor.forClass(DiagnoseTraceRequest.class);
        verify(service).diagnose(captor.capture());
        DiagnoseTraceRequest req = captor.getValue();
        assertThat(req.traceId()).isEqualTo(TRACE_ID);
        assertThat(req.businessId()).isNull();
        assertThat(req.maxSuspectEvents()).isNull();
        assertThat(req.includeSlowest()).isNull();
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.diagnose(any(DiagnoseTraceRequest.class)))
                .thenThrow(new InvalidParamException("trace_id is required"));

        Object result = tool.diagnoseTrace("  ", null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("trace_id");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse carrying upstream trace id")
    void upstreamErrorConverted() {
        when(service.diagnose(any(DiagnoseTraceRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_AUTH_FAILED, "auth failed", "req-401", null));

        Object result = tool.diagnoseTrace(TRACE_ID, BUSINESS_ID, MAX_SUSPECTS, Boolean.TRUE);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_AUTH_FAILED.name());
        assertThat(err.upstreamTraceId()).isEqualTo("req-401");
    }
}
