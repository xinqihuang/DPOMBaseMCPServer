/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

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
 * {@link ApmApplicationTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmApplicationToolTest {

    private ApmDiscoveryService service;
    private ApmApplicationTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmDiscoveryService.class);
        tool = new ApmApplicationTool(service);
    }

    @Test
    @DisplayName("Success: tool params are packed into the request DTO (page defaults to 1)")
    void successPassthrough() {
        ApmSearchApplicationResponse expected =
                new ApmSearchApplicationResponse(List.of(), 0, null);
        when(service.searchApplication(any(ApmSearchApplicationRequest.class)))
                .thenReturn(expected);

        Object result = tool.searchApmApplication(7000L, "cn-north-4", null, 50, "order");

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ApmSearchApplicationRequest> captor =
                ArgumentCaptor.forClass(ApmSearchApplicationRequest.class);
        verify(service).searchApplication(captor.capture());
        ApmSearchApplicationRequest req = captor.getValue();
        assertThat(req.businessId()).isEqualTo(7000L);
        assertThat(req.region()).isEqualTo("cn-north-4");
        assertThat(req.page()).isEqualTo(1);
        assertThat(req.pageSize()).isEqualTo(50);
        assertThat(req.keyword()).isEqualTo("order");
    }

    @Test
    @DisplayName("InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void invalidParamConverted() {
        when(service.searchApplication(any(ApmSearchApplicationRequest.class)))
                .thenThrow(new InvalidParamException("page must be >= 1, got: -1"));

        Object result = tool.searchApmApplication(7000L, null, -1, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.errorMessage()).contains("page");
        assertThat(err.retryable()).isFalse();
    }
}
