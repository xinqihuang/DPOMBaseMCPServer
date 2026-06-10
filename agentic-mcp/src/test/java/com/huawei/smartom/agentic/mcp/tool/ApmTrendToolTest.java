/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmTrendService;

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
 * {@link ApmTrendTool} 单元测试。覆盖 T22 任务卡 UT-T1 ~ UT-T3。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmTrendToolTest {

    private ApmTrendService service;
    private ApmTrendTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmTrendService.class);
        tool = new ApmTrendTool(service);
    }

    @Test
    @DisplayName("UT-T1: 7 inputs (incl. nested viewConfig) are mapped to ApmTrendRequest and response is returned")
    void ut01SuccessPassthrough() {
        ApmTrendResponse expected = new ApmTrendResponse(List.of(), 1717999860000L);
        when(service.showTrend(any(ApmTrendRequest.class))).thenReturn(expected);

        ApmTrendViewConfig view = new ApmTrendViewConfig(
                "trend", "default-collector", "latency", "Latency Trend",
                "H", "host", "env=prod", null, true, "5m",
                "time desc", "1h");
        Object result = tool.showTrend(
                7000L, 9001L, 333L, 1001L,
                "2026-06-10 10:00:00", "2026-06-10 11:00:00", view);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<ApmTrendRequest> captor = ArgumentCaptor.forClass(ApmTrendRequest.class);
        verify(service).showTrend(captor.capture());
        ApmTrendRequest req = captor.getValue();
        assertThat(req.businessId()).isEqualTo(7000L);
        assertThat(req.instanceId()).isEqualTo(9001L);
        assertThat(req.monitorItemId()).isEqualTo(333L);
        assertThat(req.envId()).isEqualTo(1001L);
        assertThat(req.startTime()).isEqualTo("2026-06-10 10:00:00");
        assertThat(req.endTime()).isEqualTo("2026-06-10 11:00:00");
        assertThat(req.viewConfig()).isSameAs(view);
    }

    @Test
    @DisplayName("UT-T2: service InvalidParamException -> ErrorResponse(INVALID_PARAM)")
    void ut02InvalidParam() {
        when(service.showTrend(any(ApmTrendRequest.class)))
                .thenThrow(new InvalidParamException("view_config is required"));

        Object result = tool.showTrend(7000L, null, null, null, "t1", "t2", null);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("view_config");
    }

    @Test
    @DisplayName("UT-T3: UpstreamException -> ErrorResponse with trace id")
    void ut03UpstreamError() {
        when(service.showTrend(any(ApmTrendRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "boom", "req-trend-1", null));

        ApmTrendViewConfig view = new ApmTrendViewConfig(
                "trend", null, "latency", null,
                null, null, null, null, null, null, null, null);
        Object result = tool.showTrend(7000L, null, null, null, "t1", "t2", view);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-trend-1");
    }
}
