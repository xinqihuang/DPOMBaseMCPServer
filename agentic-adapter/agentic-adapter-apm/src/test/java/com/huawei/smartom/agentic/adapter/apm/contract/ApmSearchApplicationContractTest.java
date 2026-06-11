/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAppInfo;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.SearchApplicationRequest;
import com.huaweicloud.sdk.apm.v1.model.SearchApplicationResponse;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * APM v1 {@code SearchApplication} 接口的 schema 契约测试（T28）。
 *
 * <p>响应侧覆盖 {@link ApmAppInfo} 全部 7 字段 + 顶层 3 字段（list / total / map）；
 * 请求侧断言 header 与 body 的 business_id 同源、null 时回落配置默认值。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmSearchApplicationContractTest {

    private ApmClient apmClient;
    private ApmDiscoveryAdapterImpl adapter;

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
        properties.setApmBusinessId(99999L);
        properties.setApmRegion("cn-north-4");

        adapter = new ApmDiscoveryAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                properties);
    }

    @Test
    @DisplayName("Adapter maps SearchApplication sample losslessly (3 top-level + 7 app fields)")
    void adapterMapsLosslessly() throws IOException {
        when(apmClient.searchApplication(any(SearchApplicationRequest.class)))
                .thenReturn(loadSample());

        ApmSearchApplicationResponse out = adapter.searchApplication(
                new ApmSearchApplicationRequest(7000L, "cn-north-4", 1, 100, "order"));

        assertThat(out.appTotalCount()).isEqualTo(1);
        assertThat(out.appInfoList()).hasSize(2);

        ApmAppInfo prod = out.appInfoList().get(0);
        assertThat(prod.envName()).isEqualTo("prod");
        assertThat(prod.envId()).isEqualTo(1306682L);
        assertThat(prod.appName()).isEqualTo("order-svc");
        assertThat(prod.appId()).isEqualTo(555001L);
        assertThat(prod.onlineCount()).isEqualTo(3);
        assertThat(prod.disableCount()).isEqualTo(1);
        assertThat(prod.offlineCount()).isEqualTo(0);

        assertThat(out.appInfoMap()).containsKey("order-svc");
        assertThat(out.appInfoMap().get("order-svc").envId()).isEqualTo(1306682L);
    }

    @Test
    @DisplayName("Header x-business-id and body business_id share the same effective value")
    void headerAndBodyBusinessIdAligned() throws IOException {
        when(apmClient.searchApplication(any(SearchApplicationRequest.class)))
                .thenReturn(loadSample());

        adapter.searchApplication(
                new ApmSearchApplicationRequest(7000L, "cn-east-3", 2, 50, null));

        ArgumentCaptor<SearchApplicationRequest> captor =
                ArgumentCaptor.forClass(SearchApplicationRequest.class);
        verify(apmClient).searchApplication(captor.capture());
        SearchApplicationRequest sdk = captor.getValue();
        assertThat(sdk.getXBusinessId()).isEqualTo(7000L);
        assertThat(sdk.getBody().getBusinessId()).isEqualTo(7000L);
        assertThat(sdk.getBody().getRegion()).isEqualTo("cn-east-3");
        assertThat(sdk.getBody().getPage()).isEqualTo(2);
        assertThat(sdk.getBody().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Null business_id and region fall back to configured defaults")
    void nullInputsFallBackToConfig() throws IOException {
        when(apmClient.searchApplication(any(SearchApplicationRequest.class)))
                .thenReturn(loadSample());

        adapter.searchApplication(
                new ApmSearchApplicationRequest(null, null, null, null, null));

        ArgumentCaptor<SearchApplicationRequest> captor =
                ArgumentCaptor.forClass(SearchApplicationRequest.class);
        verify(apmClient).searchApplication(captor.capture());
        SearchApplicationRequest sdk = captor.getValue();
        assertThat(sdk.getXBusinessId()).isEqualTo(99999L);
        assertThat(sdk.getBody().getBusinessId()).isEqualTo(99999L);
        assertThat(sdk.getBody().getRegion()).isEqualTo("cn-north-4");
        assertThat(sdk.getBody().getPage()).isEqualTo(1);
    }

    private SearchApplicationResponse loadSample() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/search-application-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, SearchApplicationResponse.class);
        }
    }
}
