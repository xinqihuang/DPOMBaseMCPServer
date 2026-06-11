/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmBusinessNode;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmListBusinessResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ListBusinessRequest;
import com.huaweicloud.sdk.apm.v1.model.ListBusinessResponse;

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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * APM v1 {@code ListBusiness} 接口的 schema 契约测试（T28）。
 *
 * <p>覆盖 {@link ApmBusinessNode} 全部 9 个字段（含 {@code default} 与 {@code is_default}
 * 两个独立布尔、{@code LocalDate} 时间字段），漂移即 fail。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmListBusinessContractTest {

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

        adapter = new ApmDiscoveryAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                new HuaweiCloudProperties());
    }

    @Test
    @DisplayName("Adapter maps ListBusiness sample losslessly (all 9 business-node fields)")
    void adapterMapsLosslessly() throws IOException {
        ListBusinessResponse sdkResp = loadSample();
        when(apmClient.listBusiness(any(ListBusinessRequest.class))).thenReturn(sdkResp);

        ApmListBusinessResponse out = adapter.listBusiness();

        assertThat(out.businessNodes()).hasSize(2);

        ApmBusinessNode full = out.businessNodes().get(0);
        assertThat(full.defaultFlag()).isFalse();
        assertThat(full.displayName()).isEqualTo("订单中心");
        assertThat(full.epsId()).isEqualTo("0");
        assertThat(full.gmtCreate()).isEqualTo(LocalDate.of(2025, 11, 3));
        assertThat(full.gmtModify()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(full.id()).isEqualTo(7000L);
        assertThat(full.innerDomainId()).isEqualTo(12345);
        assertThat(full.isDefault()).isFalse();
        assertThat(full.name()).isEqualTo("order-center");

        ApmBusinessNode minimal = out.businessNodes().get(1);
        assertThat(minimal.id()).isEqualTo(7001L);
        assertThat(minimal.name()).isEqualTo("default-business");
        assertThat(minimal.gmtCreate()).isNull();
        assertThat(minimal.defaultFlag()).isNull();
    }

    private ListBusinessResponse loadSample() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/list-business-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListBusinessResponse.class);
        }
    }
}
