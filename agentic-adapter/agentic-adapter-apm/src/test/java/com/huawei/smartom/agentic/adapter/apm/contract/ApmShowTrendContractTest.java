/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmTrendAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendLine;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendPoint;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowTrendRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowTrendResponse;

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
 * APM v1 {@code ShowTrend} 接口的 schema 契约测试。
 *
 * <p>T22 关键防线：覆盖响应侧全部字段（{@code line_list × 6} + {@code point_list × 2}
 * + 顶层 {@code latest_data_Time}），并断言 {@code value} 在 Number / String 两种运行时
 * 类型下都能透传。删任一 DTO 字段编译失败。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmShowTrendContractTest {

    private ApmClient apmClient;
    private ApmTrendAdapterImpl adapter;

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

        adapter = new ApmTrendAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                properties);
    }

    @Test
    @DisplayName("Adapter maps ShowTrend sample losslessly (lines × 6 + points × 2 + latestDataTime; value covers Number + String)")
    void adapterMapsLosslessly() throws IOException {
        ShowTrendResponse sdkResp = loadSample();
        when(apmClient.showTrend(any(ShowTrendRequest.class))).thenReturn(sdkResp);

        ApmTrendResponse out = adapter.showTrend(new ApmTrendRequest(
                7000L,
                new ApmTrendViewConfig("trend", null, "latency", null,
                        null, null, null, null, null, null, null, null),
                null, 333L, null,
                "2026-06-10 10:00:00", "2026-06-10 11:00:00"));

        assertThat(out.latestDataTime()).isEqualTo(1717999860000L);
        assertThat(out.lineList()).hasSize(2);

        ApmTrendLine line0 = out.lineList().get(0);
        assertThat(line0.title()).isEqualTo("avg(latency)");
        assertThat(line0.unit()).isEqualTo("ms");
        assertThat(line0.precision()).isEqualTo(2);
        assertThat(line0.dataType()).isEqualTo("double");
        assertThat(line0.visible()).isTrue();
        assertThat(line0.pointList()).hasSize(2);

        ApmTrendPoint p0 = line0.pointList().get(0);
        assertThat(p0.time()).isEqualTo(1717999800000L);
        assertThat(p0.value()).isInstanceOf(Number.class);
        assertThat(((Number) p0.value()).doubleValue()).isEqualTo(123.4);

        ApmTrendPoint p1 = line0.pointList().get(1);
        assertThat(p1.time()).isEqualTo(1717999860000L);
        assertThat(((Number) p1.value()).doubleValue()).isEqualTo(130.1);

        ApmTrendLine line1 = out.lineList().get(1);
        assertThat(line1.title()).isEqualTo("host_label");
        assertThat(line1.unit()).isEqualTo("");
        assertThat(line1.precision()).isEqualTo(0);
        assertThat(line1.dataType()).isEqualTo("string");
        assertThat(line1.visible()).isFalse();
        assertThat(line1.pointList()).hasSize(2);

        // String-valued points — value 必须保留 Object 类型，按运行时类型透传
        ApmTrendPoint sp0 = line1.pointList().get(0);
        assertThat(sp0.value()).isInstanceOf(String.class).isEqualTo("host-a");
        ApmTrendPoint sp1 = line1.pointList().get(1);
        assertThat(sp1.value()).isEqualTo("host-b");
    }

    private ShowTrendResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/show-trend-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowTrendResponse.class);
        }
    }
}
