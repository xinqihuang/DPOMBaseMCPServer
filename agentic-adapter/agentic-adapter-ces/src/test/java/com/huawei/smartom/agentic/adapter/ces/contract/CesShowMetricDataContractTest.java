/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.contract;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapterImpl;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDatapoint;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.model.ShowMetricDataRequest;
import com.huaweicloud.sdk.ces.v1.model.ShowMetricDataResponse;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CES v1 {@code ShowMetricData} 接口的 schema 契约测试。
 *
 * <p>T19 PR-2 防漂移：把 SDK 样本反序列化，经 adapter 映射，逐字段断言。
 * 删除 {@link CesQueryMetricDataResponse} 或 {@link CesDatapoint} 任一字段会编译失败。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class CesShowMetricDataContractTest {

    private CesClient cesClient;
    private CesMetricsAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        cesClient = mock(CesClient.class);
        com.huaweicloud.sdk.ces.v2.CesClient cesV2Client =
                mock(com.huaweicloud.sdk.ces.v2.CesClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter("ces-readonly");

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry("huaweicloud-retryable");

        adapter = new CesMetricsAdapterImpl(cesClient, cesV2Client,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()));
    }

    @Test
    @DisplayName("Adapter maps ShowMetricData sample losslessly into CesQueryMetricDataResponse")
    void mapsLosslessly() throws IOException {
        ShowMetricDataResponse sdkResp = loadSample();
        when(cesClient.showMetricData(any(ShowMetricDataRequest.class))).thenReturn(sdkResp);

        CesQueryMetricDataResponse out = adapter.queryMetricData(new CesQueryMetricDataRequest(
                "SYS.ECS", "cpu_util",
                List.of(new CesMetricDimension("instance_id", "i-abc123")),
                CesMetricFilter.AVERAGE,
                CesMetricPeriod.MIN_5,
                1718000000000L, 1718000300000L));

        assertThat(out.metricName()).isEqualTo("cpu_util");
        assertThat(out.datapoints()).hasSize(2);

        CesDatapoint p0 = out.datapoints().get(0);
        assertThat(p0.timestamp()).isEqualTo(1718000000000L);
        assertThat(p0.unit()).isEqualTo("%");
        assertThat(p0.max()).isEqualTo(87.4);
        assertThat(p0.min()).isEqualTo(21.2);
        assertThat(p0.average()).isEqualTo(55.3);
        assertThat(p0.sum()).isEqualTo(829.5);
        assertThat(p0.variance()).isEqualTo(12.4);

        CesDatapoint p1 = out.datapoints().get(1);
        assertThat(p1.timestamp()).isEqualTo(1718000300000L);
        assertThat(p1.unit()).isEqualTo("%");
        assertThat(p1.average()).isEqualTo(42.0);
        assertThat(p1.max()).isNull();
        assertThat(p1.min()).isNull();
    }

    private ShowMetricDataResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/ces/show-metric-data-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowMetricDataResponse.class);
        }
    }
}
