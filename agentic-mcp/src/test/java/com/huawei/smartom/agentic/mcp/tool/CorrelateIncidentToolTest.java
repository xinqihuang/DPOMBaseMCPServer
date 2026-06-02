/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateBranchResult;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentRequest;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentResponse;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CorrelateIncidentTool} 单元测试。
 *
 * <p>Tool 层非常薄：只负责构造 {@link CorrelateIncidentRequest}、透传服务结果以及
 * 把 {@code SmartomException} 转换为 {@link ErrorResponse}；时间窗、分支跳过等业务校验
 * 全部下沉到 {@code CorrelateIncidentService}。本测试覆盖：
 * <ul>
 *   <li>成功路径下入参完整透传到服务层</li>
 *   <li>服务层 {@link InvalidParamException} 转 {@link ErrorResponse}</li>
 *   <li>服务层 {@link UpstreamException} 转 {@link ErrorResponse} 并携带 upstream trace id</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CorrelateIncidentToolTest {

    private static final long START = 1700000000000L;
    private static final long END = 1700003600000L;
    private static final String CES_NAMESPACE = "SYS.ECS";
    private static final String LOG_CATEGORY = "app_log";
    private static final String LOG_KEYWORD = "ERROR";
    private static final String APM_TRACE_ID = "trace-abc";
    private static final String APM_SOURCE = "frontend";

    private CorrelateIncidentService service;
    private CorrelateIncidentTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CorrelateIncidentService.class);
        tool = new CorrelateIncidentTool(service);
    }

    @Test
    @DisplayName("Success: all tool params are forwarded into the request DTO unchanged")
    void successPassthrough() {
        CorrelateIncidentResponse expected = new CorrelateIncidentResponse(
                CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.ofSkipped());
        when(service.correlate(any(CorrelateIncidentRequest.class))).thenReturn(expected);

        Object result = tool.correlateIncident(
                START, END, CES_NAMESPACE, LOG_CATEGORY, LOG_KEYWORD, APM_TRACE_ID, APM_SOURCE);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CorrelateIncidentRequest> captor =
                ArgumentCaptor.forClass(CorrelateIncidentRequest.class);
        verify(service).correlate(captor.capture());
        CorrelateIncidentRequest req = captor.getValue();
        assertThat(req.startTimeMillis()).isEqualTo(START);
        assertThat(req.endTimeMillis()).isEqualTo(END);
        assertThat(req.cesNamespace()).isEqualTo(CES_NAMESPACE);
        assertThat(req.logCategory()).isEqualTo(LOG_CATEGORY);
        assertThat(req.logKeyword()).isEqualTo(LOG_KEYWORD);
        assertThat(req.apmTraceId()).isEqualTo(APM_TRACE_ID);
        assertThat(req.apmSource()).isEqualTo(APM_SOURCE);
    }

    @Test
    @DisplayName("Success: null optional params propagate as null (service decides to skip branches)")
    void successWithNullOptionals() {
        CorrelateIncidentResponse expected = new CorrelateIncidentResponse(
                CorrelateBranchResult.success("alarms"),
                CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.ofSkipped());
        when(service.correlate(any(CorrelateIncidentRequest.class))).thenReturn(expected);

        Object result = tool.correlateIncident(START, END, null, null, null, null, null);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CorrelateIncidentRequest> captor =
                ArgumentCaptor.forClass(CorrelateIncidentRequest.class);
        verify(service).correlate(captor.capture());
        CorrelateIncidentRequest req = captor.getValue();
        assertThat(req.startTimeMillis()).isEqualTo(START);
        assertThat(req.endTimeMillis()).isEqualTo(END);
        assertThat(req.cesNamespace()).isNull();
        assertThat(req.logCategory()).isNull();
        assertThat(req.logKeyword()).isNull();
        assertThat(req.apmTraceId()).isNull();
        assertThat(req.apmSource()).isNull();
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.correlate(any(CorrelateIncidentRequest.class)))
                .thenThrow(new InvalidParamException("end_time_millis must be > start_time_millis"));

        Object result = tool.correlateIncident(END, START, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("end_time_millis");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse carrying upstream trace id")
    void upstreamErrorConverted() {
        when(service.correlate(any(CorrelateIncidentRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_AUTH_FAILED, "auth failed", "req-401", null));

        Object result = tool.correlateIncident(
                START, END, CES_NAMESPACE, LOG_CATEGORY, LOG_KEYWORD, APM_TRACE_ID, APM_SOURCE);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_AUTH_FAILED.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isEqualTo("req-401");
    }
}
