/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmListBusinessResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ApmBusinessTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmBusinessToolTest {

    private ApmDiscoveryService service;
    private ApmBusinessTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmDiscoveryService.class);
        tool = new ApmBusinessTool(service);
    }

    @Test
    @DisplayName("Success: service response passed through unchanged")
    void successPassthrough() {
        ApmListBusinessResponse expected = new ApmListBusinessResponse(List.of());
        when(service.listBusiness()).thenReturn(expected);

        assertThat(tool.listApmBusiness()).isSameAs(expected);
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse with trace id")
    void upstreamErrorConverted() {
        when(service.listBusiness()).thenThrow(new UpstreamException(
                ErrorCode.UPSTREAM_AUTH_FAILED, "auth failed", "req-apm-biz", null));

        Object result = tool.listApmBusiness();

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_AUTH_FAILED.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isEqualTo("req-apm-biz");
    }
}
