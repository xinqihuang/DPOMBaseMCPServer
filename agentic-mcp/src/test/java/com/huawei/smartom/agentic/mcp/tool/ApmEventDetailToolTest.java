/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

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
 * {@link ApmEventDetailTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmEventDetailToolTest {

    private ApmTraceService service;
    private ApmEventDetailTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmTraceService.class);
        tool = new ApmEventDetailTool(service);
    }

    @Test
    @DisplayName("Success: four params packed into the request DTO")
    void successPassthrough() {
        ApmEventDetailResponse expected = new ApmEventDetailResponse(null);
        when(service.showEventDetail(any(ApmEventDetailRequest.class))).thenReturn(expected);

        Object result = tool.showEventDetail("trace-abc", "span-1", "evt-100", 1306682L);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ApmEventDetailRequest> captor =
                ArgumentCaptor.forClass(ApmEventDetailRequest.class);
        verify(service).showEventDetail(captor.capture());
        ApmEventDetailRequest req = captor.getValue();
        assertThat(req.traceId()).isEqualTo("trace-abc");
        assertThat(req.spanId()).isEqualTo("span-1");
        assertThat(req.eventId()).isEqualTo("evt-100");
        assertThat(req.envId()).isEqualTo(1306682L);
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse")
    void invalidParamConverted() {
        when(service.showEventDetail(any(ApmEventDetailRequest.class)))
                .thenThrow(new InvalidParamException("span_id is required"));

        Object result = tool.showEventDetail("trace-abc", null, "evt-100", 1306682L);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("span_id");
    }
}
