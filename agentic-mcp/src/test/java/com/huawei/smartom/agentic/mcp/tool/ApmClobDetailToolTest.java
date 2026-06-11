/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
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
 * {@link ApmClobDetailTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmClobDetailToolTest {

    private ApmTraceService service;
    private ApmClobDetailTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmTraceService.class);
        tool = new ApmClobDetailTool(service);
    }

    @Test
    @DisplayName("Success: params packed into the request DTO, business_id optional")
    void successPassthrough() {
        ApmClobDetailResponse expected = new ApmClobDetailResponse("full stack trace");
        when(service.showClobDetail(any(ApmClobDetailRequest.class))).thenReturn(expected);

        Object result = tool.showClobDetail(null, 1306682L, "clob-778");

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ApmClobDetailRequest> captor =
                ArgumentCaptor.forClass(ApmClobDetailRequest.class);
        verify(service).showClobDetail(captor.capture());
        ApmClobDetailRequest req = captor.getValue();
        assertThat(req.businessId()).isNull();
        assertThat(req.envId()).isEqualTo(1306682L);
        assertThat(req.clobId()).isEqualTo("clob-778");
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse")
    void invalidParamConverted() {
        when(service.showClobDetail(any(ApmClobDetailRequest.class)))
                .thenThrow(new InvalidParamException("clob_id is required"));

        Object result = tool.showClobDetail(7000L, 1306682L, " ");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("clob_id");
    }
}
