/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.contract;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapterImpl;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricQuery;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricResult;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDatapoint;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.model.BatchListMetricDataRequest;
import com.huaweicloud.sdk.ces.v1.model.BatchListMetricDataResponse;

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
 * CES v1 {@code BatchListMetricData} 接口的 schema 契约测试。
 *
 * <p>T19 PR-2 防漂移：覆盖 {@link CesBatchMetricResult}（5 字段）与 {@link CesDatapoint}
 * （batch 模式下 unit 字段为 null，其他 6 个统计字段直接对齐 SDK）的全部字段。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class CesBatchListMetricDataContractTest {

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
    @DisplayName("Adapter maps BatchListMetricData sample losslessly")
    void mapsLosslessly() throws IOException {
        BatchListMetricDataResponse sdkResp = loadSample();
        when(cesClient.batchListMetricData(any(BatchListMetricDataRequest.class)))
                .thenReturn(sdkResp);

        CesBatchQueryMetricDataResponse out = adapter.batchQueryMetricData(
                new CesBatchQueryMetricDataRequest(
                        List.of(new CesBatchMetricQuery("SYS.ECS", "cpu_util",
                                List.of(new CesMetricDimension("instance_id", "i-abc123")))),
                        CesMetricFilter.AVERAGE,
                        CesMetricPeriod.MIN_5,
                        1718000000000L, 1718000300000L));

        assertThat(out.metrics()).hasSize(2);

        CesBatchMetricResult m0 = out.metrics().get(0);
        assertThat(m0.namespace()).isEqualTo("SYS.ECS");
        assertThat(m0.metricName()).isEqualTo("cpu_util");
        assertThat(m0.unit()).isEqualTo("%");
        assertThat(m0.dimensions()).hasSize(1);
        assertThat(m0.dimensions().get(0).name()).isEqualTo("instance_id");
        assertThat(m0.dimensions().get(0).value()).isEqualTo("i-abc123");
        assertThat(m0.datapoints()).hasSize(1);

        CesDatapoint dp = m0.datapoints().get(0);
        assertThat(dp.timestamp()).isEqualTo(1718000000000L);
        // Batch datapoints intentionally carry no unit — unit lives on the parent metric.
        assertThat(dp.unit()).isNull();
        assertThat(dp.max()).isEqualTo(87.4);
        assertThat(dp.min()).isEqualTo(21.2);
        assertThat(dp.average()).isEqualTo(55.3);
        assertThat(dp.sum()).isEqualTo(829.5);
        assertThat(dp.variance()).isEqualTo(12.4);

        CesBatchMetricResult m1 = out.metrics().get(1);
        assertThat(m1.metricName()).isEqualTo("mem_util");
        assertThat(m1.datapoints()).isEmpty();
    }

    private BatchListMetricDataResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/ces/batch-list-metric-data-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, BatchListMetricDataResponse.class);
        }
    }
}
