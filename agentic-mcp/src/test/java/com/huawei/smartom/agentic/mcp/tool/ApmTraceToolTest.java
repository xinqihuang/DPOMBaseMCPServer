/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse;
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
 * {@link ApmTraceTool} 单元测试。
 *
 * <p>覆盖 Tool 层成功透传以及业务 / 上游异常到 {@link ErrorResponse} 的映射。
 * Tool 层本身不做参数校验，所有校验由下层 {@link ApmTraceService} 负责。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class ApmTraceToolTest {

    private static final Long BUSINESS_ID = 12345L;
    private static final String REGION = "cn-north-9";
    private static final String START = "2026-05-28 10:00:00";
    private static final String END = "2026-05-28 11:00:00";
    private static final String TRACE_ID = "trace-abc";
    private static final String SOURCE = "/api/foo";
    private static final Boolean HAS_ERROR = Boolean.TRUE;
    private static final Long TIME_USED_MIN = 200L;
    private static final Integer PAGE = 2;
    private static final Integer PAGE_SIZE = 100;

    private ApmTraceService service;
    private ApmTraceTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmTraceService.class);
        tool = new ApmTraceTool(service);
    }

    @Test
    @DisplayName("Success: request DTO is built from inputs and response is returned unchanged")
    void successPassthrough() {
        ApmQueryTracesResponse expected = new ApmQueryTracesResponse(0, List.of());
        when(service.queryTraces(any(ApmQueryTracesRequest.class))).thenReturn(expected);

        Object result = tool.queryTraces(
                BUSINESS_ID, REGION, START, END, TRACE_ID, SOURCE, HAS_ERROR,
                TIME_USED_MIN, PAGE, PAGE_SIZE);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<ApmQueryTracesRequest> captor =
                ArgumentCaptor.forClass(ApmQueryTracesRequest.class);
        verify(service).queryTraces(captor.capture());
        ApmQueryTracesRequest req = captor.getValue();
        assertThat(req.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(req.region()).isEqualTo(REGION);
        assertThat(req.startTimeString()).isEqualTo(START);
        assertThat(req.endTimeString()).isEqualTo(END);
        assertThat(req.traceId()).isEqualTo(TRACE_ID);
        assertThat(req.source()).isEqualTo(SOURCE);
        assertThat(req.hasError()).isEqualTo(HAS_ERROR);
        assertThat(req.timeUsedMin()).isEqualTo(TIME_USED_MIN);
        assertThat(req.page()).isEqualTo(PAGE);
        assertThat(req.pageSize()).isEqualTo(PAGE_SIZE);
    }

    @Test
    @DisplayName("Null businessId is allowed and defaults for page / pageSize are applied")
    void nullBusinessIdAndDefaultPaging() {
        ApmQueryTracesResponse expected = new ApmQueryTracesResponse(0, List.of());
        when(service.queryTraces(any(ApmQueryTracesRequest.class))).thenReturn(expected);

        Object result = tool.queryTraces(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<ApmQueryTracesRequest> captor =
                ArgumentCaptor.forClass(ApmQueryTracesRequest.class);
        verify(service).queryTraces(captor.capture());
        ApmQueryTracesRequest req = captor.getValue();
        assertThat(req.businessId()).isNull();
        // Compact constructor defaults: page -> 1, pageSize -> 50.
        assertThat(req.page()).isEqualTo(1);
        assertThat(req.pageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse")
    void serviceInvalidParamConverted() {
        when(service.queryTraces(any(ApmQueryTracesRequest.class)))
                .thenThrow(new InvalidParamException("page_size must be in [1, 500]"));

        Object result = tool.queryTraces(
                BUSINESS_ID, REGION, null, null, null, null, null, null, 1, 9999);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("page_size");
    }

    @Test
    @DisplayName("UpstreamException (throttled) is converted to ErrorResponse with trace id")
    void upstreamThrottledConverted() {
        when(service.queryTraces(any(ApmQueryTracesRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-trace-1", null));

        Object result = tool.queryTraces(
                BUSINESS_ID, REGION, null, null, null, null, null, null, 1, 50);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-trace-1");
    }

    @Test
    @DisplayName("UpstreamException (5xx) is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.queryTraces(any(ApmQueryTracesRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "boom", "req-trace-5xx", null));

        Object result = tool.queryTraces(
                BUSINESS_ID, REGION, null, null, null, null, null, null, 1, 50);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-trace-5xx");
    }
}
