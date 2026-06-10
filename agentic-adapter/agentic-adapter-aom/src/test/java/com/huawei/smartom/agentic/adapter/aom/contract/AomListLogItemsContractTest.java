/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.contract;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapterImpl;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.ListLogItemsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListLogItemsResponse;

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
 * AOM v2 {@code ListLogItems} 接口的 schema 契约测试。
 *
 * <p>T19 PR-4 防漂移：覆盖 {@link AomQueryLogsResponse} 全部 3 个字段（errorCode /
 * errorMessage / result）。SDK 把 result 留作未结构化 JSON 字符串透传，DTO 保持原样。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class AomListLogItemsContractTest {

    private AomClient aomClient;
    private AomMetricsAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        aomClient = mock(AomClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter("aom-readonly");

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry("huaweicloud-retryable");

        adapter = new AomMetricsAdapterImpl(aomClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()));
    }

    @Test
    @DisplayName("Adapter maps ListLogItems sample losslessly (all 3 fields)")
    void adapterMapsLosslessly() throws IOException {
        ListLogItemsResponse sdkResp = loadSample();
        when(aomClient.listLogItems(any(ListLogItemsRequest.class))).thenReturn(sdkResp);

        AomQueryLogsResponse out = adapter.queryLogs(new AomQueryLogsRequest(
                "app_log", 1718000000000L, 1718000060000L, "ERROR", 100, Boolean.TRUE));

        assertThat(out.errorCode()).isEqualTo("SVCSTG_AMS_2000000");
        assertThat(out.errorMessage()).isEqualTo("success");
        assertThat(out.result()).contains("startup ok").contains("boom");
    }

    private ListLogItemsResponse loadSample() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/aom/list-log-items-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListLogItemsResponse.class);
        }
    }
}
