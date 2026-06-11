/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.contract;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapterImpl;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDatapoint;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricPeriod;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricStatistic;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomSampleSeries;
import com.huawei.smartom.agentic.adapter.aom.dto.AomStatisticValue;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.ListSampleRequest;
import com.huaweicloud.sdk.aom.v2.model.ListSampleResponse;

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
 * AOM v2 {@code ListSample} 接口的 schema 契约测试。
 *
 * <p>T19 PR-4 防漂移：覆盖整个 sample 树：
 * {@code ListSampleResponse.samples} → {@code SampleDataValue.sample} (QuerySample 3 fields,
 * 在 DTO 中拍平到 {@link AomSampleSeries}) + {@code SampleDataValue.data_points}
 * （{@code MetricDataPoints} 3 字段 → {@link AomMetricDatapoint}，含 {@code StatisticValue} 2 字段
 * → {@link AomStatisticValue}）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class AomListSampleContractTest {

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
    @DisplayName("Adapter maps ListSample sample losslessly (samples + dataPoints + statistics)")
    void adapterMapsLosslessly() throws IOException {
        ListSampleResponse sdkResp = loadSample();
        when(aomClient.listSample(any(ListSampleRequest.class))).thenReturn(sdkResp);

        AomQueryMetricDataResponse out = adapter.queryMetricData(new AomQueryMetricDataRequest(
                "PAAS.CONTAINER", "aom_process_cpu_usage", null,
                List.of(AomMetricStatistic.MAXIMUM, AomMetricStatistic.AVERAGE,
                        AomMetricStatistic.MINIMUM),
                AomMetricPeriod.SEC_60, "-1.-1.60", null));

        assertThat(out.series()).hasSize(1);

        AomSampleSeries series = out.series().get(0);
        assertThat(series.namespace()).isEqualTo("PAAS.CONTAINER");
        assertThat(series.metricName()).isEqualTo("aom_process_cpu_usage");
        assertThat(series.dimensions()).hasSize(2);
        assertThat(series.dimensions().get(0).name()).isEqualTo("appName");
        assertThat(series.dimensions().get(0).value()).isEqualTo("aomApp");
        assertThat(series.dimensions().get(1).name()).isEqualTo("podName");
        assertThat(series.dimensions().get(1).value()).isEqualTo("aomApp-7d8b-xyz");

        assertThat(series.datapoints()).hasSize(2);

        AomMetricDatapoint dp0 = series.datapoints().get(0);
        assertThat(dp0.timestamp()).isEqualTo(1718000000000L);
        assertThat(dp0.unit()).isEqualTo("Percent");
        assertThat(dp0.statistics()).hasSize(3);
        AomStatisticValue max = dp0.statistics().get(0);
        assertThat(max.statistic()).isEqualTo("maximum");
        assertThat(max.value()).isEqualTo(87.4);
        assertThat(dp0.statistics().get(1).statistic()).isEqualTo("average");
        assertThat(dp0.statistics().get(1).value()).isEqualTo(55.3);
        assertThat(dp0.statistics().get(2).statistic()).isEqualTo("minimum");
        assertThat(dp0.statistics().get(2).value()).isEqualTo(21.2);

        AomMetricDatapoint dp1 = series.datapoints().get(1);
        assertThat(dp1.timestamp()).isEqualTo(1718000060000L);
        assertThat(dp1.unit()).isEqualTo("Percent");
        assertThat(dp1.statistics()).hasSize(1);
        assertThat(dp1.statistics().get(0).statistic()).isEqualTo("average");
        assertThat(dp1.statistics().get(0).value()).isEqualTo(42.0);
    }

    private ListSampleResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/aom/list-sample-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListSampleResponse.class);
        }
    }
}
