/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendFieldItem;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmViewBase;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmViewRow;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowMonitorItemViewConfigRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowMonitorItemViewConfigResponse;

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
 * APM v1 {@code ShowMonitorItemViewConfig} 接口的 schema 契约测试。
 *
 * <p>T23 防线：覆盖 {@link ApmMonitorItemViewConfigResponse} 全 4 字段、
 * {@link ApmViewRow} 全 2 字段、{@link ApmViewBase} 全 12 字段、{@link ApmTrendFieldItem}
 * 关键字段（function/as/visible）。样本来自任务卡末附 2（env 1306682 JVM 实测）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmShowMonitorItemViewConfigContractTest {

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

        adapter = new ApmDiscoveryAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                properties);
    }

    @Test
    @DisplayName("Adapter maps ShowMonitorItemViewConfig sample losslessly (rows×4, views×9 incl. sumtable with latest=true)")
    void adapterMapsLosslessly() throws IOException {
        ShowMonitorItemViewConfigResponse sdkResp = loadSample();
        when(apmClient.showMonitorItemViewConfig(any(ShowMonitorItemViewConfigRequest.class)))
                .thenReturn(sdkResp);

        ApmMonitorItemViewConfigResponse out = adapter.showMonitorItemViewConfig(28L, 1306682L, 7000L);

        assertThat(out.title()).isEqualTo("JVM");
        assertThat(out.collectorName()).isEqualTo("JVM");
        assertThat(out.viewRowList()).hasSize(4);

        // Row 0: 线程 + 线程状态
        ApmViewRow row0 = out.viewRowList().get(0);
        assertThat(row0.title()).isEmpty();
        assertThat(row0.viewList()).hasSize(2);
        ApmViewBase thread = row0.viewList().get(0);
        assertThat(thread.collectorName()).isEqualTo("JVM");
        assertThat(thread.metricSet()).isEqualTo("thread");
        assertThat(thread.title()).isEqualTo("线程");
        assertThat(thread.viewType()).isEqualTo("trend");
        assertThat(thread.groupBy()).isEmpty();
        assertThat(thread.filter()).isEmpty();
        assertThat(thread.fieldItemList()).hasSize(5);
        ApmTrendFieldItem fi0 = thread.fieldItemList().get(0);
        assertThat(fi0.function()).isEqualTo("MAX(threadCount)");
        assertThat(fi0.as()).isEqualTo("当前线程数");
        assertThat(fi0.visible()).isTrue();

        // Row 3: 内存池（sumtable，latest=true，tableDirection=H）—— 关键校验 latest 是 Boolean
        ApmViewRow row3 = out.viewRowList().get(3);
        assertThat(row3.viewList()).hasSize(1);
        ApmViewBase memPool = row3.viewList().get(0);
        assertThat(memPool.viewType()).isEqualTo("sumtable");
        assertThat(memPool.tableDirection()).isEqualTo("H");
        assertThat(memPool.groupBy()).isEqualTo("name");
        assertThat(memPool.orderBy()).isEmpty();
        assertThat(memPool.latest()).isTrue();
        assertThat(memPool.metricSet()).isEqualTo("memoryPool");
        assertThat(memPool.fieldItemList()).hasSize(4);
    }

    private ShowMonitorItemViewConfigResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/show-monitor-item-view-config-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowMonitorItemViewConfigResponse.class);
        }
    }
}
