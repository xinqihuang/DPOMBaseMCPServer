/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyResponse;
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
 * {@link ApmAlarmNotifyTool} 单元测试。覆盖 T21 任务卡 UT-T1 ~ UT-T3。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmAlarmNotifyToolTest {

    private ApmAlarmService service;
    private ApmAlarmNotifyTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmAlarmService.class);
        tool = new ApmAlarmNotifyTool(service);
    }

    @Test
    @DisplayName("UT-T1: all 5 inputs are mapped to ApmAlarmNotifyRequest and response is returned")
    void ut01SuccessPassthrough() {
        ApmAlarmNotifyResponse expected = new ApmAlarmNotifyResponse(List.of(), 0);
        when(service.listAlarmNotify(any(ApmAlarmNotifyRequest.class))).thenReturn(expected);

        Object result = tool.listAlarmNotify(7000L, 12345, 1, 50, "cn-north-4");
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<ApmAlarmNotifyRequest> captor =
                ArgumentCaptor.forClass(ApmAlarmNotifyRequest.class);
        verify(service).listAlarmNotify(captor.capture());
        ApmAlarmNotifyRequest req = captor.getValue();
        assertThat(req.businessId()).isEqualTo(7000L);
        assertThat(req.alarmDataId()).isEqualTo(12345);
        assertThat(req.page()).isEqualTo(1);
        assertThat(req.pageSize()).isEqualTo(50);
        assertThat(req.region()).isEqualTo("cn-north-4");
    }

    @Test
    @DisplayName("UT-T2: service InvalidParamException -> ErrorResponse(INVALID_PARAM)")
    void ut02InvalidParam() {
        when(service.listAlarmNotify(any(ApmAlarmNotifyRequest.class)))
                .thenThrow(new InvalidParamException("alarm_data_id is required"));

        Object result = tool.listAlarmNotify(7000L, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("alarm_data_id");
    }

    @Test
    @DisplayName("UT-T3: UpstreamException -> ErrorResponse with trace id")
    void ut03UpstreamError() {
        when(service.listAlarmNotify(any(ApmAlarmNotifyRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "boom", "req-apm-2", null));

        Object result = tool.listAlarmNotify(7000L, 12345, null, null, null);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-apm-2");
    }
}
