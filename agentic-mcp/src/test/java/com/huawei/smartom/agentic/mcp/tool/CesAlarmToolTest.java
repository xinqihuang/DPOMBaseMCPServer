/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.ces.CesAlarmService;

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
 * {@link CesAlarmTool} 单元测试。
 *
 * <p>覆盖 Tool 层的成功透传（构造的请求 DTO 字段与入参一致）以及
 * 业务异常 / 上游异常到 {@link ErrorResponse} 的映射。Tool 层本身不做参数校验，
 * 所有校验由下层 service 负责，因此异常分支均通过 mock service 抛出来驱动。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CesAlarmToolTest {

    private static final String GROUP_ID = "rg1234567890123456789012";
    private static final String ALARM_ID = "al1234567890123456789012";
    private static final String ALARM_NAME = "cpu-high";
    private static final String ALARM_STATUS = "alarm";
    private static final Integer ALARM_LEVEL = 2;
    private static final String NAMESPACE = "SYS.ECS";
    private static final String FROM = "1700000000000";
    private static final String TO = "1700003600000";
    private static final Integer START = 0;
    private static final Integer LIMIT = 50;

    private CesAlarmService service;
    private CesAlarmTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesAlarmService.class);
        tool = new CesAlarmTool(service);
    }

    @Test
    @DisplayName("Success: request DTO is built from inputs and response is returned unchanged")
    void successPassthrough() {
        CesListAlarmsResponse expected = new CesListAlarmsResponse(List.of(), 0);
        when(service.listAlarms(any(CesListAlarmsRequest.class))).thenReturn(expected);

        Object result = tool.listAlarms(
                GROUP_ID, ALARM_ID, ALARM_NAME, ALARM_STATUS, ALARM_LEVEL,
                NAMESPACE, FROM, TO, START, LIMIT);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesListAlarmsRequest> captor =
                ArgumentCaptor.forClass(CesListAlarmsRequest.class);
        verify(service).listAlarms(captor.capture());
        CesListAlarmsRequest req = captor.getValue();
        assertThat(req.groupId()).isEqualTo(GROUP_ID);
        assertThat(req.alarmId()).isEqualTo(ALARM_ID);
        assertThat(req.alarmName()).isEqualTo(ALARM_NAME);
        assertThat(req.alarmStatus()).isEqualTo(ALARM_STATUS);
        assertThat(req.alarmLevel()).isEqualTo(ALARM_LEVEL);
        assertThat(req.namespace()).isEqualTo(NAMESPACE);
        assertThat(req.from()).isEqualTo(FROM);
        assertThat(req.to()).isEqualTo(TO);
        assertThat(req.start()).isEqualTo(START);
        assertThat(req.limit()).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("Null optional params still produce a request and the response is passed through")
    void successWithAllOptionalNulls() {
        CesListAlarmsResponse expected = new CesListAlarmsResponse(List.of(), 0);
        when(service.listAlarms(any(CesListAlarmsRequest.class))).thenReturn(expected);

        Object result = tool.listAlarms(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesListAlarmsRequest> captor =
                ArgumentCaptor.forClass(CesListAlarmsRequest.class);
        verify(service).listAlarms(captor.capture());
        CesListAlarmsRequest req = captor.getValue();
        // Compact constructor defaults: limit -> 100, start -> 0.
        assertThat(req.limit()).isEqualTo(100);
        assertThat(req.start()).isZero();
        assertThat(req.groupId()).isNull();
        assertThat(req.alarmStatus()).isNull();
        assertThat(req.alarmLevel()).isNull();
        assertThat(req.namespace()).isNull();
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse")
    void serviceInvalidParamConverted() {
        when(service.listAlarms(any(CesListAlarmsRequest.class)))
                .thenThrow(new InvalidParamException(
                        "alarm_status must be one of ok/alarm/insufficient_data/invalid, got: foo"));

        Object result = tool.listAlarms(
                null, null, null, "foo", null, null, null, null, 0, 50);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("alarm_status");
    }

    @Test
    @DisplayName("UpstreamException (throttled) is converted to ErrorResponse with trace id")
    void upstreamThrottledConverted() {
        when(service.listAlarms(any(CesListAlarmsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-abc", null));

        Object result = tool.listAlarms(
                null, null, null, null, null, NAMESPACE, null, null, 0, 50);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-abc");
    }

    @Test
    @DisplayName("UpstreamException (5xx) is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.listAlarms(any(CesListAlarmsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "boom", "req-5xx", null));

        Object result = tool.listAlarms(
                null, null, null, null, null, NAMESPACE, null, null, 0, 50);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-5xx");
    }
}
