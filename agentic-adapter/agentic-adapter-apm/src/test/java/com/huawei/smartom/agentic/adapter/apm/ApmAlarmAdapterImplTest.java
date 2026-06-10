/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.AlarmDataVO;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmDataRequest;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmDataResponse;
import com.huaweicloud.sdk.core.exception.ClientRequestException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServerResponseException;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApmAlarmAdapterImpl} 单元测试。
 *
 * <p>覆盖 T20 任务卡 UT-A1（mapping）+ UT-A2（异常映射 4 case）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmAlarmAdapterImplTest {

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
    @DisplayName("UT-A1: full request — 14 body fields aligned + x-business-id header injected")
    void ut01FullRequestMapping() {
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenReturn(new ListAlarmDataResponse().withTotalCount(0).withAlarmDataList(List.of()));

        ApmAlarmDataRequest req = new ApmAlarmDataRequest(
                7000L, 2, 50, "cn-north-4", "order-svc", 7000L,
                333L, "ALARM", "MAJOR", "latency",
                "2026-06-10 10:00:00", "2026-06-10 11:00:00",
                5, "10.0.0.42", List.of(1001L, 1002L));
        adapter.listAlarmData(req);

        ArgumentCaptor<ListAlarmDataRequest> captor = ArgumentCaptor.forClass(ListAlarmDataRequest.class);
        verify(apmClient, times(1)).listAlarmData(captor.capture());
        ListAlarmDataRequest sdkReq = captor.getValue();
        assertThat(sdkReq.getXBusinessId()).isEqualTo(7000L);
        assertThat(sdkReq.getBody().getPage()).isEqualTo(2);
        assertThat(sdkReq.getBody().getPageSize()).isEqualTo(50);
        assertThat(sdkReq.getBody().getRegion()).isEqualTo("cn-north-4");
        assertThat(sdkReq.getBody().getAppName()).isEqualTo("order-svc");
        assertThat(sdkReq.getBody().getBusinessId()).isEqualTo(7000L);
        assertThat(sdkReq.getBody().getMonitorItemId()).isEqualTo(333L);
        assertThat(sdkReq.getBody().getStatus()).isEqualTo("ALARM");
        assertThat(sdkReq.getBody().getAlarmLevel()).isEqualTo("MAJOR");
        assertThat(sdkReq.getBody().getKeyword()).isEqualTo("latency");
        assertThat(sdkReq.getBody().getAlarmStartTime()).isEqualTo("2026-06-10 10:00:00");
        assertThat(sdkReq.getBody().getAlarmEndTime()).isEqualTo("2026-06-10 11:00:00");
        assertThat(sdkReq.getBody().getCollectorId()).isEqualTo(5);
        assertThat(sdkReq.getBody().getIpAddress()).isEqualTo("10.0.0.42");
        assertThat(sdkReq.getBody().getEnvList()).containsExactly(1001L, 1002L);
    }

    @Test
    @DisplayName("UT-A1b: null businessId falls back to huaweicloud.apm-business-id config")
    void ut01bBusinessIdFallback() {
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenReturn(new ListAlarmDataResponse().withTotalCount(0).withAlarmDataList(List.of()));

        adapter.listAlarmData(minimalRequest(null));

        ArgumentCaptor<ListAlarmDataRequest> captor = ArgumentCaptor.forClass(ListAlarmDataRequest.class);
        verify(apmClient).listAlarmData(captor.capture());
        assertThat(captor.getValue().getXBusinessId()).isEqualTo(99999L);
    }

    @Test
    @DisplayName("UT-A1c: response mapping — all 27 AlarmDataVO fields surface in ApmAlarm")
    void ut01cResponseLossless() {
        AlarmDataVO sdk = new AlarmDataVO()
                .withId(12345L)
                .withGmtCreate("2026-06-10 10:00:00")
                .withRegionAlarmEventId(67890L)
                .withBusinessName("order-service")
                .withAppName("order-svc")
                .withVersionNumber(3)
                .withAlarmRuleType("STATIC")
                .withGmtModify("2026-06-10 10:30:00")
                .withProcessUnit("order-pod-7d8b")
                .withRegion("cn-north-4")
                .withInstanceId(9001L)
                .withIpAddress("10.0.0.42")
                .withInstanceName("order-pod-7d8b-xyz")
                .withEnvId(1001L)
                .withBusinessId(7000L)
                .withTemplateId(444L)
                .withAlarmRuleId(22222L)
                .withMonitorItemId(333L)
                .withCollectorId(5)
                .withCollectorName("default-collector")
                .withAlarmRuleName("high-latency")
                .withAlarmRuleExpression("avg(latency) > 500")
                .withAlarmFirstTime("2026-06-10 10:00:00")
                .withAlarmLastTime("2026-06-10 10:30:00")
                .withAlarmLevel("MAJOR")
                .withRestrainKey("rk-abc")
                .withStatus("ALARM");
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenReturn(new ListAlarmDataResponse().withTotalCount(1).withAlarmDataList(List.of(sdk)));

        ApmAlarmDataResponse out = adapter.listAlarmData(minimalRequest(7000L));
        assertThat(out.totalCount()).isEqualTo(1);
        assertThat(out.alarms()).hasSize(1);
        var alarm = out.alarms().get(0);
        assertThat(alarm.id()).isEqualTo(12345L);
        assertThat(alarm.gmtCreate()).isEqualTo("2026-06-10 10:00:00");
        assertThat(alarm.regionAlarmEventId()).isEqualTo(67890L);
        assertThat(alarm.businessName()).isEqualTo("order-service");
        assertThat(alarm.appName()).isEqualTo("order-svc");
        assertThat(alarm.versionNumber()).isEqualTo(3);
        assertThat(alarm.alarmRuleType()).isEqualTo("STATIC");
        assertThat(alarm.gmtModify()).isEqualTo("2026-06-10 10:30:00");
        assertThat(alarm.processUnit()).isEqualTo("order-pod-7d8b");
        assertThat(alarm.region()).isEqualTo("cn-north-4");
        assertThat(alarm.instanceId()).isEqualTo(9001L);
        assertThat(alarm.ipAddress()).isEqualTo("10.0.0.42");
        assertThat(alarm.instanceName()).isEqualTo("order-pod-7d8b-xyz");
        assertThat(alarm.envId()).isEqualTo(1001L);
        assertThat(alarm.businessId()).isEqualTo(7000L);
        assertThat(alarm.templateId()).isEqualTo(444L);
        assertThat(alarm.alarmRuleId()).isEqualTo(22222L);
        assertThat(alarm.monitorItemId()).isEqualTo(333L);
        assertThat(alarm.collectorId()).isEqualTo(5);
        assertThat(alarm.collectorName()).isEqualTo("default-collector");
        assertThat(alarm.alarmRuleName()).isEqualTo("high-latency");
        assertThat(alarm.alarmRuleExpression()).isEqualTo("avg(latency) > 500");
        assertThat(alarm.alarmFirstTime()).isEqualTo("2026-06-10 10:00:00");
        assertThat(alarm.alarmLastTime()).isEqualTo("2026-06-10 10:30:00");
        assertThat(alarm.alarmLevel()).isEqualTo("MAJOR");
        assertThat(alarm.restrainKey()).isEqualTo("rk-abc");
        assertThat(alarm.status()).isEqualTo("ALARM");
    }

    @Test
    @DisplayName("UT-A2a: HTTP 429 -> retried 3x then UPSTREAM_THROTTLED (retryable)")
    void ut02aThrottled() {
        AtomicInteger calls = new AtomicInteger();
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(429, "APM.0429", "throttled", "req-429");
                });
        assertThatThrownBy(() -> adapter.listAlarmData(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED)
                .matches(ex -> "req-429".equals(((SmartomException) ex).getUpstreamTraceId()));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-A2b: HTTP 401 -> no retry, UPSTREAM_AUTH_FAILED")
    void ut02bAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(401, "APIGW.0301", "unauthorized", "req-401");
                });
        assertThatThrownBy(() -> adapter.listAlarmData(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-A2c: HTTP 5xx -> retried 3x then UPSTREAM_ERROR")
    void ut02cServerError() {
        AtomicInteger calls = new AtomicInteger();
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ServerResponseException(503, "APM.5030", "unavailable", "req-503");
                });
        assertThatThrownBy(() -> adapter.listAlarmData(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-A2d: SDK timeout -> TIMEOUT")
    void ut02dTimeout() {
        when(apmClient.listAlarmData(any(ListAlarmDataRequest.class)))
                .thenThrow(new RequestTimeoutException("read timed out"));
        assertThatThrownBy(() -> adapter.listAlarmData(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.TIMEOUT);
    }

    private ApmAlarmDataRequest minimalRequest(Long businessId) {
        return new ApmAlarmDataRequest(
                businessId, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
