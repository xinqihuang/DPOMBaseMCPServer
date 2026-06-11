/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomAlertType;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.aom.AomEventService;

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
 * {@link AomEventTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomEventToolTest {

    private AomEventService service;
    private AomEventTool tool;

    @BeforeEach
    void setUp() {
        service = mock(AomEventService.class);
        tool = new AomEventTool(service);
    }

    @Test
    @DisplayName("Success: enum type and all params passed through to the service")
    void successPassthrough() {
        AomListEventsResponse expected = new AomListEventsResponse(List.of(), null);
        when(service.listEvents(any(AomListEventsRequest.class))).thenReturn(expected);

        Object result = tool.listAomEvents(
                AomAlertType.ACTIVE_ALERT, "-1.-1.60", 60000L, "cpu",
                List.of("starts_at"), "desc", null, 100, "0");

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<AomListEventsRequest> captor =
                ArgumentCaptor.forClass(AomListEventsRequest.class);
        verify(service).listEvents(captor.capture());
        AomListEventsRequest req = captor.getValue();
        assertThat(req.type()).isEqualTo(AomAlertType.ACTIVE_ALERT);
        assertThat(req.timeRange()).isEqualTo("-1.-1.60");
        assertThat(req.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void invalidParamConverted() {
        when(service.listEvents(any(AomListEventsRequest.class)))
                .thenThrow(new InvalidParamException("time_range format invalid"));

        Object result = tool.listAomEvents(
                null, "bad", null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.errorMessage()).contains("time_range");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.listEvents(any(AomListEventsRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-aom-events", null));

        Object result = tool.listAomEvents(
                AomAlertType.HISTORY_ALERT, "-1.-1.60", null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-aom-events");
    }
}
