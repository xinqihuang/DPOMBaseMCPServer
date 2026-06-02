/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricQuery;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.ces.CesBatchMetricDataService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CesBatchMetricDataTool} 单元测试。
 *
 * <p>覆盖 Tool 层的基本校验（filter/period 必填与枚举解析）、成功透传以及异常→ErrorResponse 映射。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CesBatchMetricDataToolTest {

    private static final List<CesBatchMetricQuery> METRICS = List.of(
            new CesBatchMetricQuery("SYS.ECS", "cpu_util",
                    List.of(new CesMetricDimension("instance_id", "i-1"))),
            new CesBatchMetricQuery("SYS.ECS", "mem_util",
                    List.of(new CesMetricDimension("instance_id", "i-2"))));

    private static final long FROM = 1700000000000L;
    private static final long TO = 1700003600000L;

    private CesBatchMetricDataService service;
    private CesBatchMetricDataTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesBatchMetricDataService.class);
        tool = new CesBatchMetricDataTool(service);
    }

    @Test
    @DisplayName("Success: filter/period strings parsed to enums and request passed through")
    void successPassthrough() {
        CesBatchQueryMetricDataResponse expected = new CesBatchQueryMetricDataResponse(List.of());
        when(service.batchQueryMetricData(any(CesBatchQueryMetricDataRequest.class)))
                .thenReturn(expected);

        Object result = tool.batchQueryCesMetricData(METRICS, "max", 3600, FROM, TO);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesBatchQueryMetricDataRequest> captor =
                ArgumentCaptor.forClass(CesBatchQueryMetricDataRequest.class);
        verify(service).batchQueryMetricData(captor.capture());
        CesBatchQueryMetricDataRequest req = captor.getValue();
        assertThat(req.filter()).isEqualTo(CesMetricFilter.MAX);
        assertThat(req.period()).isEqualTo(CesMetricPeriod.HOUR_1);
        assertThat(req.metrics()).hasSize(2);
    }

    @Test
    @DisplayName("Null filter -> INVALID_PARAM without invoking service")
    void nullFilterRejected() {
        Object result = tool.batchQueryCesMetricData(METRICS, null, 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("filter");
        verify(service, never()).batchQueryMetricData(any());
    }

    @Test
    @DisplayName("Null period -> INVALID_PARAM without invoking service")
    void nullPeriodRejected() {
        Object result = tool.batchQueryCesMetricData(METRICS, "average", null, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("period");
        verify(service, never()).batchQueryMetricData(any());
    }

    @Test
    @DisplayName("Unknown filter -> INVALID_PARAM")
    void unknownFilterRejected() {
        Object result = tool.batchQueryCesMetricData(METRICS, "p95", 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("p95");
        verify(service, never()).batchQueryMetricData(any());
    }

    @Test
    @DisplayName("Unknown period -> INVALID_PARAM")
    void unknownPeriodRejected() {
        Object result = tool.batchQueryCesMetricData(METRICS, "average", 7, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("7");
        verify(service, never()).batchQueryMetricData(any());
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse")
    void serviceInvalidParamConverted() {
        when(service.batchQueryMetricData(any(CesBatchQueryMetricDataRequest.class)))
                .thenThrow(new InvalidParamException("metrics is required (at least 1)"));

        Object result = tool.batchQueryCesMetricData(List.of(), "average", 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.batchQueryMetricData(any(CesBatchQueryMetricDataRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "server error", "req-503", null));

        Object result = tool.batchQueryCesMetricData(METRICS, "average", 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-503");
    }
}
