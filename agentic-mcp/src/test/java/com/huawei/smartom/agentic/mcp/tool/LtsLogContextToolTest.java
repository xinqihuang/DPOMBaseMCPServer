/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.lts.LtsLogContextService;

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
 * {@link LtsLogContextTool} 单元测试。
 *
 * <p>覆盖 T18 任务卡 UT-T1 ~ UT-T3：成功透传 7 个入参、{@code InvalidParamException} →
 * {@code ErrorResponse(INVALID_PARAM)}、{@code UpstreamException} → 携带 trace id。
 *
 * @author h00884391
 * @since 2026-06-03
 */
class LtsLogContextToolTest {

    private LtsLogContextService service;
    private LtsLogContextTool tool;

    @BeforeEach
    void setUp() {
        service = mock(LtsLogContextService.class);
        tool = new LtsLogContextTool(service);
    }

    @Test
    @DisplayName("UT-T1: all 7 inputs are mapped to LtsListLogContextRequest, response passed through")
    void ut01SuccessPassthrough() {
        LtsListLogContextResponse expected = new LtsListLogContextResponse(
                List.of(), 0, 0, 0, Boolean.TRUE);
        when(service.queryContext(any(LtsListLogContextRequest.class))).thenReturn(expected);

        Object result = tool.queryLtsLogContext(
                "lg-1", "ls-1", "100", "1700000000000", 50, 100, "scroll-x");

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<LtsListLogContextRequest> captor =
                ArgumentCaptor.forClass(LtsListLogContextRequest.class);
        verify(service).queryContext(captor.capture());
        LtsListLogContextRequest req = captor.getValue();
        assertThat(req.logGroupId()).isEqualTo("lg-1");
        assertThat(req.logStreamId()).isEqualTo("ls-1");
        assertThat(req.lineNum()).isEqualTo("100");
        assertThat(req.cursorTime()).isEqualTo("1700000000000");
        assertThat(req.backwardsSize()).isEqualTo(50);
        assertThat(req.forwardsSize()).isEqualTo(100);
        assertThat(req.scrollId()).isEqualTo("scroll-x");
    }

    @Test
    @DisplayName("UT-T2: service InvalidParamException is converted to ErrorResponse(INVALID_PARAM)")
    void ut02InvalidParamConverted() {
        when(service.queryContext(any(LtsListLogContextRequest.class)))
                .thenThrow(new InvalidParamException(
                        "either (line_num + cursor_time) or scroll_id is required"));

        Object result = tool.queryLtsLogContext(
                "lg-1", "ls-1", null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("line_num");
    }

    @Test
    @DisplayName("UT-T3: UpstreamException is converted to ErrorResponse with trace id")
    void ut03UpstreamErrorConverted() {
        when(service.queryContext(any(LtsListLogContextRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-lts-ctx-1", null));

        Object result = tool.queryLtsLogContext(
                "lg-1", "ls-1", "100", "1700000000000", null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-lts-ctx-1");
    }
}
