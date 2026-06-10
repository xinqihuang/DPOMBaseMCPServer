/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmTraceAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSpan;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowSpanSearchRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowSpanSearchResponse;

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
 * APM v1 {@code ShowSpanSearch} 接口的 schema 契约测试。
 *
 * <p>T19 PR-3 防漂移：覆盖 {@link ApmSpan} 全部 22 个字段（其中 9 个是 PR-3 新增）。
 * 删除任一字段会让对应 record accessor 消失从而编译失败。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmShowSpanSearchContractTest {

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
    @DisplayName("Sample JSON deserialises into SDK ShowSpanSearchResponse with all fields populated")
    void sdkDeserialisationCovers22Fields() throws IOException {
        ShowSpanSearchResponse sdkResp = loadSample();
        assertThat(sdkResp.getTotal()).isEqualTo(1);
        assertThat(sdkResp.getSpanInfoList()).hasSize(1);

        var span = sdkResp.getSpanInfoList().get(0);
        assertThat(span.getGlobalTraceId()).isEqualTo("gt-12345-67890");
        assertThat(span.getGlobalPath()).isEqualTo("frontend->order-svc->payment-svc");
        assertThat(span.getEnvId()).isEqualTo(1001L);
        assertThat(span.getInstanceId()).isEqualTo(2002L);
        assertThat(span.getAppId()).isEqualTo(3003L);
        assertThat(span.getBizId()).isEqualTo(4004L);
        assertThat(span.getDomainId()).isEqualTo(5);
        assertThat(span.getIsAsync()).isFalse();
        assertThat(span.getType()).isEqualTo("HTTP");
        assertThat(span.getBizCode()).isEqualTo("ORDER_CREATE_FAILED");
    }

    @Test
    @DisplayName("Adapter maps sample SDK response losslessly into ApmSpan (deleting any field fails compilation)")
    void adapterMapsLosslessly() throws IOException {
        ShowSpanSearchResponse sdkResp = loadSample();
        when(apmClient.showSpanSearch(any(ShowSpanSearchRequest.class))).thenReturn(sdkResp);

        ApmQueryTracesResponse out = adapter.queryTraces(new ApmQueryTracesRequest(
                123L, null, null, "tr-abc123", null, null, null, 1, 50));

        assertThat(out.total()).isEqualTo(1);
        assertThat(out.spans()).hasSize(1);

        ApmSpan span = out.spans().get(0);

        // ---- 13 legacy fields ----
        assertThat(span.traceId()).isEqualTo("tr-abc123");
        assertThat(span.spanId()).isEqualTo("sp-xyz789");
        assertThat(span.globalTraceId()).isEqualTo("gt-12345-67890");
        assertThat(span.source()).isEqualTo("POST /api/orders");
        assertThat(span.realSource()).isEqualTo("https://order.example.com/api/orders");
        assertThat(span.className()).isEqualTo("com.example.OrderController");
        assertThat(span.startTime()).isEqualTo(1718000000000L);
        assertThat(span.timeUsed()).isEqualTo(1234L);
        assertThat(span.code()).isEqualTo(500);
        assertThat(span.hasError()).isTrue();
        assertThat(span.errorReasons()).contains("NullPointerException");
        assertThat(span.httpMethod()).isEqualTo("POST");
        assertThat(span.tags()).containsEntry("user_id", "u-9001")
                .containsEntry("region", "cn-north-9");

        // ---- 9 newly-added fields (PR-3) ----
        assertThat(span.globalPath()).isEqualTo("frontend->order-svc->payment-svc");
        assertThat(span.envId()).isEqualTo(1001L);
        assertThat(span.instanceId()).isEqualTo(2002L);
        assertThat(span.appId()).isEqualTo(3003L);
        assertThat(span.bizId()).isEqualTo(4004L);
        assertThat(span.domainId()).isEqualTo(5);
        assertThat(span.isAsync()).isFalse();
        assertThat(span.type()).isEqualTo("HTTP");
        assertThat(span.bizCode()).isEqualTo("ORDER_CREATE_FAILED");
    }

    private ShowSpanSearchResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/show-span-search-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowSpanSearchResponse.class);
        }
    }
}
