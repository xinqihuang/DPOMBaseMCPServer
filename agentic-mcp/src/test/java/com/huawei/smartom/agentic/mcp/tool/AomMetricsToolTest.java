/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPagination;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.aom.AomMetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AOM 指标工具单元测试。
 *
 * @author h00884391
 * @since 2026-05-21
 */
class AomMetricsToolTest {

    private AomMetricsService service;
    private AomMetricsTool tool;

    @BeforeEach
    void setUp() {
        service = mock(AomMetricsService.class);
        tool = new AomMetricsTool(service);
    }

    @Test
    @DisplayName("Successful call returns the service response unchanged")
    void successPassthrough() {
        AomListMetricsResponse expected = new AomListMetricsResponse(
                Collections.emptyList(), new AomPagination(0, 0, null, null, false));
        when(service.listMetrics(any(AomListMetricsRequest.class))).thenReturn(expected);

        Object result = tool.listAomMetrics("PAAS.CONTAINER", null, null, null, 10, null);
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void invalidParamConvertedToErrorResponse() {
        when(service.listMetrics(any(AomListMetricsRequest.class)))
                .thenThrow(new InvalidParamException("namespace or inventory_id must be provided"));

        Object result = tool.listAomMetrics(null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("namespace or inventory_id");
        assertThat(err.upstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamThrottledConverted() {
        when(service.listMetrics(any(AomListMetricsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-xyz", null));

        Object result = tool.listAomMetrics("PAAS.CONTAINER", null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-xyz");
    }
}
