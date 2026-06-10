/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmAlarmAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotification;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmNotifyRequest;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmNotifyResponse;

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
 * APM v1 {@code ListAlarmNotify} 接口的 schema 契约测试。
 *
 * <p>T21 关键防线：覆盖 {@link ApmAlarmNotification} 全部 8 个字段，删任一字段编译失败。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmListAlarmNotifyContractTest {

    private ApmClient apmClient;
    private ApmAlarmAdapterImpl adapter;

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

        adapter = new ApmAlarmAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                properties);
    }

    @Test
    @DisplayName("Adapter maps ListAlarmNotify sample losslessly (all 8 fields, both notify_status=true/false)")
    void adapterMapsLosslessly() throws IOException {
        ListAlarmNotifyResponse sdkResp = loadSample();
        when(apmClient.listAlarmNotify(any(ListAlarmNotifyRequest.class))).thenReturn(sdkResp);

        ApmAlarmNotifyResponse out = adapter.listAlarmNotify(new ApmAlarmNotifyRequest(
                7000L, 12345, 1, 50, "cn-north-4"));

        assertThat(out.totalCount()).isEqualTo(4);
        assertThat(out.notifications()).hasSize(2);

        ApmAlarmNotification n0 = out.notifications().get(0);
        assertThat(n0.id()).isEqualTo(100001L);
        assertThat(n0.gmtCreate()).isEqualTo("2026-06-10 10:00:05");
        assertThat(n0.notifyType()).isEqualTo("SMS");
        assertThat(n0.alarmRuleId()).isEqualTo(22222L);
        assertThat(n0.templateId()).isEqualTo(444L);
        assertThat(n0.alarmDataEventId()).isEqualTo(67890L);
        assertThat(n0.notifyStatus()).isTrue();
        assertThat(n0.alarmContent()).contains("latency > 500ms");

        ApmAlarmNotification n1 = out.notifications().get(1);
        assertThat(n1.id()).isEqualTo(100002L);
        assertThat(n1.notifyType()).isEqualTo("EMAIL");
        assertThat(n1.templateId()).isEqualTo(445L);
        assertThat(n1.notifyStatus()).isFalse();
    }

    private ListAlarmNotifyResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/list-alarm-notify-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListAlarmNotifyResponse.class);
        }
    }
}
