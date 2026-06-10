/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApmEnvMonitorItemsTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmEnvMonitorItemsToolTest {

    private ApmDiscoveryService service;
    private ApmEnvMonitorItemsTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmDiscoveryService.class);
        tool = new ApmEnvMonitorItemsTool(service);
    }

    @Test
    @DisplayName("UT-T1: success passthrough")
    void ut01Success() {
        ApmEnvMonitorItemsResponse expected = new ApmEnvMonitorItemsResponse(List.of(), List.of());
        when(service.getEnvMonitorItems(eq(1306682L), eq(7000L))).thenReturn(expected);

        Object result = tool.showEnvMonitorItems(1306682L, 7000L);
        assertThat(result).isSameAs(expected);
        verify(service).getEnvMonitorItems(1306682L, 7000L);
    }

    @Test
    @DisplayName("UT-T2: InvalidParamException -> ErrorResponse")
    void ut02InvalidParam() {
        when(service.getEnvMonitorItems(eq(null), eq(7000L)))
                .thenThrow(new InvalidParamException("env_id is required"));

        Object result = tool.showEnvMonitorItems(null, 7000L);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
    }

    @Test
    @DisplayName("UT-T3: UpstreamException -> ErrorResponse with trace id")
    void ut03Upstream() {
        when(service.getEnvMonitorItems(eq(1306682L), eq(7000L)))
                .thenThrow(new UpstreamException(ErrorCode.UPSTREAM_ERROR, "boom", "req-env-1", null));

        Object result = tool.showEnvMonitorItems(1306682L, 7000L);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.upstreamTraceId()).isEqualTo("req-env-1");
    }
}
