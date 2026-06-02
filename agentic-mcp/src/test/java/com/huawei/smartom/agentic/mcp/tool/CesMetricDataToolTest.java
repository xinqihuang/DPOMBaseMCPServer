/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.ces.CesMetricDataService;

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
 * {@link CesMetricDataTool} 单元测试。
 *
 * <p>覆盖 Tool 层的基本校验（filter/period 必填与枚举解析）、成功透传以及异常→ErrorResponse 映射。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CesMetricDataToolTest {

    private static final String NS = "SYS.ECS";
    private static final String METRIC = "cpu_util";
    private static final List<CesMetricDimension> DIMS =
            List.of(new CesMetricDimension("instance_id", "i-1"));
    private static final long FROM = 1700000000000L;
    private static final long TO = 1700003600000L;

    private CesMetricDataService service;
    private CesMetricDataTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesMetricDataService.class);
        tool = new CesMetricDataTool(service);
    }

    @Test
    @DisplayName("Success: filter/period strings parsed to enums and request passed through")
    void successPassthrough() {
        CesQueryMetricDataResponse expected = new CesQueryMetricDataResponse(METRIC, List.of());
        when(service.queryMetricData(any(CesQueryMetricDataRequest.class))).thenReturn(expected);

        Object result = tool.queryCesMetricData(
                NS, METRIC, DIMS, "average", 300, FROM, TO);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesQueryMetricDataRequest> captor =
                ArgumentCaptor.forClass(CesQueryMetricDataRequest.class);
        verify(service).queryMetricData(captor.capture());
        CesQueryMetricDataRequest req = captor.getValue();
        assertThat(req.filter()).isEqualTo(CesMetricFilter.AVERAGE);
        assertThat(req.period()).isEqualTo(CesMetricPeriod.MIN_5);
        assertThat(req.namespace()).isEqualTo(NS);
        assertThat(req.metricName()).isEqualTo(METRIC);
    }

    @Test
    @DisplayName("Null filter -> INVALID_PARAM without invoking service")
    void nullFilterRejected() {
        Object result = tool.queryCesMetricData(NS, METRIC, DIMS, null, 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("filter");
        verify(service, never()).queryMetricData(any());
    }

    @Test
    @DisplayName("Null period -> INVALID_PARAM without invoking service")
    void nullPeriodRejected() {
        Object result = tool.queryCesMetricData(NS, METRIC, DIMS, "average", null, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("period");
        verify(service, never()).queryMetricData(any());
    }

    @Test
    @DisplayName("Unknown filter -> INVALID_PARAM, message mentions allowed set")
    void unknownFilterRejected() {
        Object result = tool.queryCesMetricData(NS, METRIC, DIMS, "median", 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("median");
        verify(service, never()).queryMetricData(any());
    }

    @Test
    @DisplayName("Unknown period -> INVALID_PARAM")
    void unknownPeriodRejected() {
        Object result = tool.queryCesMetricData(NS, METRIC, DIMS, "average", 42, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("42");
        verify(service, never()).queryMetricData(any());
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse")
    void serviceInvalidParamConverted() {
        when(service.queryMetricData(any(CesQueryMetricDataRequest.class)))
                .thenThrow(new InvalidParamException("from must be strictly less than to"));

        Object result = tool.queryCesMetricData(NS, METRIC, DIMS, "average", 300, TO, FROM);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.queryMetricData(any(CesQueryMetricDataRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-xyz", null));

        Object result = tool.queryCesMetricData(NS, METRIC, DIMS, "average", 300, FROM, TO);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-xyz");
    }
}
