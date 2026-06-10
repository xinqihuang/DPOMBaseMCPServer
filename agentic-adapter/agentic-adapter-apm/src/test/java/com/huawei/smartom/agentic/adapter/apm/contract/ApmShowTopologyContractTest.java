/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmTraceAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTopologyLine;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTopologyNode;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowTopologyRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowTopologyResponse;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * APM v1 {@code ShowTopology} 接口的 schema 契约测试。
 *
 * <p>T19 PR-3 防漂移：覆盖 {@link ApmTopologyLine} 全部 13 个字段（5 个 PR-3 新增）以及
 * {@link ApmTopologyNode} 与 {@link ApmGetTopologyResponse}。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmShowTopologyContractTest {

    private ApmClient apmClient;
    private ApmTraceAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        apmClient = mock(ApmClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter("apm-readonly");

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry("huaweicloud-retryable");

        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setApmRegion("cn-north-4");

        adapter = new ApmTraceAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                properties);
    }

    @Test
    @DisplayName("Adapter maps ShowTopology sample losslessly (nodes + lines + line_info)")
    void adapterMapsLosslessly() throws IOException {
        ShowTopologyResponse sdkResp = loadSample();
        when(apmClient.showTopology(any(ShowTopologyRequest.class))).thenReturn(sdkResp);

        ApmGetTopologyResponse out = adapter.getTopology(new ApmGetTopologyRequest("tr-abc123"));

        // ---- top-level ----
        assertThat(out.globalTraceId()).isEqualTo("gt-12345-67890");
        assertThat(out.nodes()).hasSize(2);
        assertThat(out.lines()).hasSize(1);

        // ---- nodes ----
        ApmTopologyNode node0 = out.nodes().get(0);
        assertThat(node0.nodeId()).isEqualTo(1L);
        assertThat(node0.nodeName()).isEqualTo("frontend");
        assertThat(node0.hint()).isEqualTo("entry node");

        ApmTopologyNode node1 = out.nodes().get(1);
        assertThat(node1.nodeId()).isEqualTo(2L);
        assertThat(node1.nodeName()).isEqualTo("order-svc");
        assertThat(node1.hint()).isNull();

        // ---- line ----
        ApmTopologyLine line = out.lines().get(0);
        // 8 legacy fields
        assertThat(line.startNodeId()).isEqualTo(1L);
        assertThat(line.endNodeId()).isEqualTo(2L);
        assertThat(line.spanId()).isEqualTo("sp-line-001");
        assertThat(line.clientStartTime()).isEqualTo(1718000000000L);
        assertThat(line.clientTimeUsed()).isEqualTo(1234L);
        assertThat(line.serverStartTime()).isEqualTo(1718000000100L);
        assertThat(line.serverTimeUsed()).isEqualTo(1100L);
        assertThat(line.hint()).isEqualTo("POST /api/orders");
        // 5 newly-added fields (PR-3)
        assertThat(line.id()).isEqualTo("edge-1-2");
        assertThat(line.clientArgument()).isEqualTo("{\"user_id\":\"u-9001\"}");
        assertThat(line.clientEventId()).isEqualTo("evt-client-001");
        assertThat(line.serverArgument()).isEqualTo("{\"order_id\":\"o-7777\"}");
        assertThat(line.serverEventId()).isEqualTo("evt-server-001");
    }

    private ShowTopologyResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/show-topology-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowTopologyResponse.class);
        }
    }
}
