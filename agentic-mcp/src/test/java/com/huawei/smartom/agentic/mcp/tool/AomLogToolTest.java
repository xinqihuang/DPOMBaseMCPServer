/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.aom.AomLogService;

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
 * {@link AomLogTool} 单元测试。
 *
 * <p>覆盖 Tool 层的成功透传（请求 DTO 构造正确）以及 service 抛出 {@link InvalidParamException} /
 * {@link UpstreamException} 时到 {@link ErrorResponse} 的映射。
 *
 * @author h00884391
 * @since 2026-06-02
 */
class AomLogToolTest {

    private static final String CATEGORY = "app_log";
    private static final long START = 1700000000000L;
    private static final long END = 1700003600000L;
    private static final String KEY_WORD = "*ERROR*";
    private static final Integer PAGE_SIZE = 200;
    private static final Boolean IS_DESC = Boolean.FALSE;

    private AomLogService service;
    private AomLogTool tool;

    @BeforeEach
    void setUp() {
        service = mock(AomLogService.class);
        tool = new AomLogTool(service);
    }

    @Test
    @DisplayName("Success: request DTO is built from tool params and returned response is passed through")
    void successPassthrough() {
        AomQueryLogsResponse expected =
                new AomQueryLogsResponse("[]", "SVCSTG_AMS_2000000", null);
        when(service.queryLogs(any(AomQueryLogsRequest.class))).thenReturn(expected);

        Object result = tool.queryLogs(CATEGORY, START, END, KEY_WORD, PAGE_SIZE, IS_DESC);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<AomQueryLogsRequest> captor =
                ArgumentCaptor.forClass(AomQueryLogsRequest.class);
        verify(service).queryLogs(captor.capture());
        AomQueryLogsRequest req = captor.getValue();
        assertThat(req.category()).isEqualTo(CATEGORY);
        assertThat(req.startTime()).isEqualTo(START);
        assertThat(req.endTime()).isEqualTo(END);
        assertThat(req.keyWord()).isEqualTo(KEY_WORD);
        assertThat(req.pageSize()).isEqualTo(PAGE_SIZE);
        assertThat(req.isDesc()).isEqualTo(IS_DESC);
    }

    @Test
    @DisplayName("Optional params default at the DTO level (pageSize=100, isDesc=true)")
    void optionalParamsDefaultedAtDto() {
        AomQueryLogsResponse expected =
                new AomQueryLogsResponse("[]", "SVCSTG_AMS_2000000", null);
        when(service.queryLogs(any(AomQueryLogsRequest.class))).thenReturn(expected);

        Object result = tool.queryLogs(CATEGORY, START, END, null, null, null);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<AomQueryLogsRequest> captor =
                ArgumentCaptor.forClass(AomQueryLogsRequest.class);
        verify(service).queryLogs(captor.capture());
        AomQueryLogsRequest req = captor.getValue();
        assertThat(req.keyWord()).isNull();
        assertThat(req.pageSize()).isEqualTo(100);
        assertThat(req.isDesc()).isTrue();
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.queryLogs(any(AomQueryLogsRequest.class)))
                .thenThrow(new InvalidParamException(
                        "end_time must be strictly greater than start_time"));

        Object result = tool.queryLogs(CATEGORY, END, START, KEY_WORD, PAGE_SIZE, IS_DESC);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("end_time");
        assertThat(err.upstreamTraceId()).isNull();
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id and retryable=true")
    void upstreamErrorConverted() {
        when(service.queryLogs(any(AomQueryLogsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "server error", "req-log-9", null));

        Object result = tool.queryLogs(CATEGORY, START, END, KEY_WORD, PAGE_SIZE, IS_DESC);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-log-9");
    }
}
