/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.monitoring.discovery.DiscoveryRequest;
import com.huawei.smartom.agentic.monitoring.discovery.ResourceContext;
import com.huawei.smartom.agentic.monitoring.discovery.ResourceDiscoveryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一资源发现工具测试：委托 service、无锚点映射为 ErrorResponse。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class UnifiedResourceDiscoveryToolTest {

    private ResourceDiscoveryService service;
    private UnifiedResourceDiscoveryTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ResourceDiscoveryService.class);
        tool = new UnifiedResourceDiscoveryTool(service);
    }

    @Test
    @DisplayName("discover_resource_context 委托 service 并返回上下文")
    void discoverDelegates() {
        ResourceContext expected = new ResourceContext(List.of(), List.of(), List.of());
        when(service.discover(any(DiscoveryRequest.class))).thenReturn(expected);

        Object result = tool.discoverResourceContext(null, null, null, "cluster-1", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isSameAs(expected);
        verify(service).discover(any(DiscoveryRequest.class));
    }

    @Test
    @DisplayName("无锚点时 service 异常映射为 ErrorResponse")
    void emptyAnchorsConvertedToError() {
        when(service.discover(any(DiscoveryRequest.class)))
                .thenThrow(new InvalidParamException("at least one discovery anchor is required"));

        Object result = tool.discoverResourceContext(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
    }

    @Test
    @DisplayName("resolve_resource_candidates 委托 service")
    void resolveDelegates() {
        when(service.resolve(any(DiscoveryRequest.class))).thenReturn(List.of());

        Object result = tool.resolveResourceCandidates(null, null, "svc", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(List.class);
        verify(service).resolve(any(DiscoveryRequest.class));
    }
}
