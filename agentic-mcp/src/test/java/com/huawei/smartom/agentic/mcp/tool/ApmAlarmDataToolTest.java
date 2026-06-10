/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmAlarmService;

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
 * {@link ApmAlarmDataTool} 单元测试。覆盖 T20 任务卡 UT-T1 ~ UT-T3。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmAlarmDataToolTest {

    private ApmAlarmService service;
    private ApmAlarmDataTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmAlarmService.class);
        tool = new ApmAlarmDataTool(service);
    }

    @Test
    @DisplayName("UT-T1: all 15 inputs are mapped to ApmAlarmDataRequest and response is returned")
    void ut01SuccessPassthrough() {
        ApmAlarmDataResponse expected = new ApmAlarmDataResponse(List.of(), 0);
        when(service.listAlarmData(any(ApmAlarmDataRequest.class))).thenReturn(expected);

        Object result = tool.listApmAlarmData(
                7000L, 2, 50, "cn-north-4", "order-svc", 8000L,
                333L, "ALARM", "MAJOR", "latency",
                "2026-06-10 10:00:00", "2026-06-10 11:00:00",
                5, "10.0.0.42", List.of(1001L, 1002L));

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<ApmAlarmDataRequest> captor = ArgumentCaptor.forClass(ApmAlarmDataRequest.class);
        verify(service).listAlarmData(captor.capture());
        ApmAlarmDataRequest req = captor.getValue();
        assertThat(req.businessId()).isEqualTo(7000L);
        assertThat(req.page()).isEqualTo(2);
        assertThat(req.pageSize()).isEqualTo(50);
        assertThat(req.region()).isEqualTo("cn-north-4");
        assertThat(req.appName()).isEqualTo("order-svc");
        assertThat(req.businessIdFilter()).isEqualTo(8000L);
        assertThat(req.monitorItemId()).isEqualTo(333L);
        assertThat(req.status()).isEqualTo("ALARM");
        assertThat(req.alarmLevel()).isEqualTo("MAJOR");
        assertThat(req.keyword()).isEqualTo("latency");
        assertThat(req.alarmStartTime()).isEqualTo("2026-06-10 10:00:00");
        assertThat(req.alarmEndTime()).isEqualTo("2026-06-10 11:00:00");
        assertThat(req.collectorId()).isEqualTo(5);
        assertThat(req.ipAddress()).isEqualTo("10.0.0.42");
        assertThat(req.envList()).containsExactly(1001L, 1002L);
    }

    @Test
    @DisplayName("UT-T2: service InvalidParamException -> ErrorResponse(INVALID_PARAM)")
    void ut02InvalidParam() {
        when(service.listAlarmData(any(ApmAlarmDataRequest.class)))
                .thenThrow(new InvalidParamException("page must be >= 1"));

        Object result = tool.listApmAlarmData(
                7000L, 0, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("page");
    }

    @Test
    @DisplayName("UT-T3: UpstreamException -> ErrorResponse with trace id")
    void ut03UpstreamError() {
        when(service.listAlarmData(any(ApmAlarmDataRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-apm-1", null));

        Object result = tool.listApmAlarmData(
                7000L, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-apm-1");
    }
}
