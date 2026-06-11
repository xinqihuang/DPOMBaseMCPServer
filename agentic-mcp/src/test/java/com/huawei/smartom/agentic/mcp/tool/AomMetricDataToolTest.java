/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomFillValue;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricPeriod;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricStatistic;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.aom.AomMetricDataService;

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
 * {@link AomMetricDataTool} 单元测试。
 *
 * <p>覆盖 Tool 层的成功透传（请求 DTO 构造正确）以及 service 抛出 {@link InvalidParamException} /
 * {@link UpstreamException} 时到 {@link ErrorResponse} 的映射。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class AomMetricDataToolTest {

    private static final String NAMESPACE = "PAAS.CONTAINER";
    private static final String METRIC = "cpuUsage";
    private static final List<AomMetricDimension> DIMS =
            List.of(new AomMetricDimension("appId", "app-1"));
    private static final List<AomMetricStatistic> STATS =
            List.of(AomMetricStatistic.AVERAGE, AomMetricStatistic.MAXIMUM);
    private static final AomMetricPeriod PERIOD = AomMetricPeriod.SEC_60;
    private static final String TIME_RANGE = "-1.-1.60";
    private static final AomFillValue FILL = AomFillValue.AVERAGE;

    private AomMetricDataService service;
    private AomMetricDataTool tool;

    @BeforeEach
    void setUp() {
        service = mock(AomMetricDataService.class);
        tool = new AomMetricDataTool(service);
    }

    @Test
    @DisplayName("Success: request DTO is built from tool params and returned response is passed through")
    void successPassthrough() {
        AomQueryMetricDataResponse expected = new AomQueryMetricDataResponse(List.of());
        when(service.queryMetricData(any(AomQueryMetricDataRequest.class))).thenReturn(expected);

        Object result = tool.queryAomMetricData(
                NAMESPACE, METRIC, DIMS, STATS, PERIOD, TIME_RANGE, FILL);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<AomQueryMetricDataRequest> captor =
                ArgumentCaptor.forClass(AomQueryMetricDataRequest.class);
        verify(service).queryMetricData(captor.capture());
        AomQueryMetricDataRequest req = captor.getValue();
        assertThat(req.namespace()).isEqualTo(NAMESPACE);
        assertThat(req.metricName()).isEqualTo(METRIC);
        assertThat(req.dimensions()).isEqualTo(DIMS);
        assertThat(req.statistics()).isEqualTo(STATS);
        assertThat(req.period()).isEqualTo(PERIOD);
        assertThat(req.timeRange()).isEqualTo(TIME_RANGE);
        assertThat(req.fillValue()).isEqualTo(FILL);
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.queryMetricData(any(AomQueryMetricDataRequest.class)))
                .thenThrow(new InvalidParamException(
                        "period must be 60/300/900/3600 (seconds), got: 42"));

        Object result = tool.queryAomMetricData(
                NAMESPACE, METRIC, DIMS, STATS, PERIOD, TIME_RANGE, FILL);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("period");
        assertThat(err.upstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id and retryable=true")
    void upstreamErrorConverted() {
        when(service.queryMetricData(any(AomQueryMetricDataRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-aom-1", null));

        Object result = tool.queryAomMetricData(
                NAMESPACE, METRIC, DIMS, STATS, PERIOD, TIME_RANGE, FILL);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-aom-1");
    }
}
