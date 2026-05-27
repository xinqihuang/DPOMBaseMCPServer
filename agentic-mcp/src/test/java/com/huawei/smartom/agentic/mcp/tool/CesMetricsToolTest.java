/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPagination;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.ces.CesMetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ces Metrics Tool Test.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class CesMetricsToolTest {

    private CesMetricsService service;
    private CesMetricsTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesMetricsService.class);
        tool = new CesMetricsTool(service);
    }

    @Test
    @DisplayName("Successful call returns the service response unchanged")
    void successPassthrough() {
        CesListMetricsResponse expected =
                new CesListMetricsResponse(List.of(), new CesPagination(0, 0, null, false));
        when(service.listMetrics(any(CesListMetricsRequest.class))).thenReturn(expected);

        Object result = tool.listCesMetrics(
                "SYS.ECS", null, null, null, 10, null, "desc");
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void invalidParamConvertedToErrorResponse() {
        when(service.listMetrics(any(CesListMetricsRequest.class)))
                .thenThrow(new InvalidParamException("limit must be in [1,1000]"));

        Object result = tool.listCesMetrics(null, null, null, null, 9999, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("limit");
        assertThat(err.upstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.listMetrics(any(CesListMetricsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-xyz", null));

        Object result = tool.listCesMetrics(null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-xyz");
    }
}
