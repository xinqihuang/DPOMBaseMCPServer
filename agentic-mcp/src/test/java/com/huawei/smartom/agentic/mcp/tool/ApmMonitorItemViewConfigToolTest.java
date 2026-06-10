/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse;
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
 * {@link ApmMonitorItemViewConfigTool} 单元测试。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmMonitorItemViewConfigToolTest {

    private ApmDiscoveryService service;
    private ApmMonitorItemViewConfigTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ApmDiscoveryService.class);
        tool = new ApmMonitorItemViewConfigTool(service);
    }

    @Test
    @DisplayName("UT-T1: success passthrough (collectorId Long -> service)")
    void ut01Success() {
        ApmMonitorItemViewConfigResponse expected = new ApmMonitorItemViewConfigResponse(
                "JVM", "JVM", List.of(), null);
        when(service.getMonitorItemViewConfig(eq(28L), eq(1306682L), eq(7000L)))
                .thenReturn(expected);

        Object result = tool.showMonitorItemViewConfig(28L, 1306682L, 7000L);
        assertThat(result).isSameAs(expected);
        verify(service).getMonitorItemViewConfig(28L, 1306682L, 7000L);
    }

    @Test
    @DisplayName("UT-T2: InvalidParamException -> ErrorResponse")
    void ut02InvalidParam() {
        when(service.getMonitorItemViewConfig(eq(null), eq(1L), eq(7000L)))
                .thenThrow(new InvalidParamException("collector_id is required"));

        Object result = tool.showMonitorItemViewConfig(null, 1L, 7000L);
        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
    }

    @Test
    @DisplayName("UT-T3: UpstreamException -> ErrorResponse")
    void ut03Upstream() {
        when(service.getMonitorItemViewConfig(eq(28L), eq(1306682L), eq(7000L)))
                .thenThrow(new UpstreamException(ErrorCode.TIMEOUT, "slow", "req-vc-1", null));

        Object result = tool.showMonitorItemViewConfig(28L, 1306682L, 7000L);
        assertThat(((ErrorResponse) result).errorCode()).isEqualTo(ErrorCode.TIMEOUT.name());
        assertThat(((ErrorResponse) result).upstreamTraceId()).isEqualTo("req-vc-1");
    }
}
