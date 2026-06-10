/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmAlarmAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarm;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmDataRequest;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmDataResponse;

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
 * APM v1 {@code ListAlarmData} 接口的 schema 契约测试。
 *
 * <p>T20 关键防线：覆盖 {@link ApmAlarm} 全部 27 个 SDK 字段，删任一字段会让对应 accessor 消失
 * 从而编译失败。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmListAlarmDataContractTest {

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
    @DisplayName("Adapter maps ListAlarmData sample losslessly into ApmAlarm (all 27 fields)")
    void adapterMapsLosslessly() throws IOException {
        ListAlarmDataResponse sdkResp = loadSample();
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class))).thenReturn(sdkResp);

        ApmAlarmDataResponse out = adapter.listAlarmData(new ApmAlarmDataRequest(
                7000L, 1, 50, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(out.totalCount()).isEqualTo(137);
        assertThat(out.alarms()).hasSize(1);

        ApmAlarm a = out.alarms().get(0);
        assertThat(a.id()).isEqualTo(12345L);
        assertThat(a.gmtCreate()).isEqualTo("2026-06-10 10:00:00");
        assertThat(a.regionAlarmEventId()).isEqualTo(67890L);
        assertThat(a.businessName()).isEqualTo("order-service");
        assertThat(a.appName()).isEqualTo("order-svc");
        assertThat(a.versionNumber()).isEqualTo(3);
        assertThat(a.alarmRuleType()).isEqualTo("STATIC");
        assertThat(a.gmtModify()).isEqualTo("2026-06-10 10:30:00");
        assertThat(a.processUnit()).isEqualTo("order-pod-7d8b");
        assertThat(a.region()).isEqualTo("cn-north-4");
        assertThat(a.instanceId()).isEqualTo(9001L);
        assertThat(a.ipAddress()).isEqualTo("10.0.0.42");
        assertThat(a.instanceName()).isEqualTo("order-pod-7d8b-xyz");
        assertThat(a.envId()).isEqualTo(1001L);
        assertThat(a.businessId()).isEqualTo(7000L);
        assertThat(a.templateId()).isEqualTo(444L);
        assertThat(a.alarmRuleId()).isEqualTo(22222L);
        assertThat(a.monitorItemId()).isEqualTo(333L);
        assertThat(a.collectorId()).isEqualTo(5);
        assertThat(a.collectorName()).isEqualTo("default-collector");
        assertThat(a.alarmRuleName()).isEqualTo("high-latency");
        assertThat(a.alarmRuleExpression()).isEqualTo("avg(latency) > 500");
        assertThat(a.alarmFirstTime()).isEqualTo("2026-06-10 10:00:00");
        assertThat(a.alarmLastTime()).isEqualTo("2026-06-10 10:30:00");
        assertThat(a.alarmLevel()).isEqualTo("MAJOR");
        assertThat(a.restrainKey()).isEqualTo("rk-abc");
        assertThat(a.status()).isEqualTo("ALARM");
    }

    private ListAlarmDataResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/list-alarm-data-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListAlarmDataResponse.class);
        }
    }
}
