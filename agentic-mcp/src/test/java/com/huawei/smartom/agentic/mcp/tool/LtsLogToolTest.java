/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.lts.LtsLogService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LtsLogTool} 单元测试。
 *
 * <p>覆盖 T17 任务卡 UT-T1 ~ UT-T3：成功透传、{@code InvalidParamException} →
 * {@code ErrorResponse(INVALID_PARAM)}、{@code UpstreamException} → 携带 trace id 的 ErrorResponse。
 *
 * @author h00884391
 * @since 2026-06-03
 */
class LtsLogToolTest {

    private LtsLogService service;
    private LtsLogTool tool;

    @BeforeEach
    void setUp() {
        service = mock(LtsLogService.class);
        tool = new LtsLogTool(service);
    }

    @Test
    @DisplayName("UT-T1: all 17 inputs are mapped to LtsListLogsRequest and service response is returned")
    void ut01SuccessPassthrough() {
        LtsListLogsResponse expected =
                new LtsListLogsResponse(2, List.of(), Boolean.TRUE, List.of());
        when(service.queryLogs(any(LtsListLogsRequest.class))).thenReturn(expected);

        Object result = tool.queryLtsLogs(
                "lg-1", "ls-1",
                1700000000000L, 1700003600000L,
                Map.of("hostname", "node-1"),
                "ERROR", "select * from t",
                Boolean.TRUE, Boolean.FALSE, 500,
                Boolean.TRUE, Boolean.FALSE, Boolean.FALSE,
                "forwards", "100", "1700000000000", "scroll-x");

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<LtsListLogsRequest> captor = ArgumentCaptor.forClass(LtsListLogsRequest.class);
        verify(service).queryLogs(captor.capture());
        LtsListLogsRequest req = captor.getValue();
        assertThat(req.logGroupId()).isEqualTo("lg-1");
        assertThat(req.logStreamId()).isEqualTo("ls-1");
        assertThat(req.startTimeMillis()).isEqualTo(1700000000000L);
        assertThat(req.endTimeMillis()).isEqualTo(1700003600000L);
        assertThat(req.labels()).containsEntry("hostname", "node-1");
        assertThat(req.keywords()).isEqualTo("ERROR");
        assertThat(req.query()).isEqualTo("select * from t");
        assertThat(req.isAnalysisQuery()).isTrue();
        assertThat(req.isCount()).isFalse();
        assertThat(req.limit()).isEqualTo(500);
        assertThat(req.isDesc()).isTrue();
        assertThat(req.highlight()).isFalse();
        assertThat(req.isIterative()).isFalse();
        assertThat(req.searchType()).isEqualTo("forwards");
        assertThat(req.lineNum()).isEqualTo("100");
        assertThat(req.cursorTime()).isEqualTo("1700000000000");
        assertThat(req.scrollId()).isEqualTo("scroll-x");
    }

    @Test
    @DisplayName("UT-T2: service InvalidParamException is converted to ErrorResponse(INVALID_PARAM)")
    void ut02InvalidParamConverted() {
        when(service.queryLogs(any(LtsListLogsRequest.class)))
                .thenThrow(new InvalidParamException("log_group_id is required"));

        Object result = tool.queryLtsLogs(
                "  ", "ls-1", null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("log_group_id");
    }

    @Test
    @DisplayName("UT-T3: UpstreamException is converted to ErrorResponse with trace id")
    void ut03UpstreamErrorConverted() {
        when(service.queryLogs(any(LtsListLogsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-lts-1", null));

        Object result = tool.queryLtsLogs(
                "lg-1", "ls-1", null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-lts-1");
    }
}
