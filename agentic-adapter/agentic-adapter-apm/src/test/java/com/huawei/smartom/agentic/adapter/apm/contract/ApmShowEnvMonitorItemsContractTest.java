/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmCollectorCategoryInfo;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemEntity;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowEnvMonitorItemsRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowEnvMonitorItemsResponse;

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
 * APM v1 {@code ShowEnvMonitorItems} 接口的 schema 契约测试。
 *
 * <p>T23 防线：覆盖 {@link ApmCollectorCategoryInfo} 全 4 字段 +
 * {@link ApmMonitorItemEntity} 全 9 字段。样本来自任务卡末附 1（实测响应）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmShowEnvMonitorItemsContractTest {

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
    @DisplayName("Adapter maps ShowEnvMonitorItems sample losslessly (category × 4 + monitorItem × 9)")
    void adapterMapsLosslessly() throws IOException {
        ShowEnvMonitorItemsResponse sdkResp = loadSample();
        when(apmClient.showEnvMonitorItems(any(ShowEnvMonitorItemsRequest.class))).thenReturn(sdkResp);

        ApmEnvMonitorItemsResponse out = adapter.showEnvMonitorItems(1306682L, 7000L);

        assertThat(out.categoryInfoList()).hasSize(5);
        ApmCollectorCategoryInfo cat0 = out.categoryInfoList().get(0);
        assertThat(cat0.categoryId()).isEqualTo(7);
        assertThat(cat0.categoryName()).isEqualTo("Url");
        assertThat(cat0.displayName()).isEqualTo("接口调用");
        assertThat(cat0.sequence()).isEqualTo(1);

        assertThat(out.monitorItemInfoList()).hasSize(9);

        // 验证 monitor_item_id=12 (Exception, collector_id=18) — 任务卡反复强调的「env 局部」例子
        ApmMonitorItemEntity exception = out.monitorItemInfoList().stream()
                .filter(it -> it.monitorItemId() == 12L)
                .findFirst()
                .orElseThrow();
        assertThat(exception.collectorId()).isEqualTo(18);
        assertThat(exception.collectorName()).isEqualTo("Exception");
        assertThat(exception.displayName()).isEqualTo("异常日志");
        assertThat(exception.categoryId()).isEqualTo(4);
        assertThat(exception.disabled()).isFalse();
        assertThat(exception.sequence()).isEqualTo(20);
        assertThat(exception.collectInterval()).isEqualTo(60);
        assertThat(exception.showInTotal()).isTrue();

        // 验证 JVM（同 env collector_id=28，对照证明 18≠JVM）
        ApmMonitorItemEntity jvm = out.monitorItemInfoList().stream()
                .filter(it -> it.monitorItemId() == 14L)
                .findFirst()
                .orElseThrow();
        assertThat(jvm.collectorId()).isEqualTo(28);
        assertThat(jvm.collectorName()).isEqualTo("JVM");
    }

    private ShowEnvMonitorItemsResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/show-env-monitor-items-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowEnvMonitorItemsResponse.class);
        }
    }
}
