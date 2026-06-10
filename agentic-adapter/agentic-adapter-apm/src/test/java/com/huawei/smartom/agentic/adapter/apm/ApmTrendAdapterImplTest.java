/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendFieldItem;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowTrendRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowTrendResponse;
import com.huaweicloud.sdk.apm.v1.model.TrendView;
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
 * {@link ApmTrendAdapterImpl} 单元测试。
 *
 * <p>覆盖 T22 任务卡 UT-A1（viewConfig 全字段映射 + header 注入）+ UT-A2（异常映射 4 case）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmTrendAdapterImplTest {

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
    @DisplayName("UT-A1: full request — viewConfig 12 fields + fieldItem 7 fields + outer 5 fields + header injected")
    void ut01FullRequestMapping() {
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenReturn(new ShowTrendResponse().withLineList(List.of()).withLatestDataTime(0L));

        ApmTrendFieldItem fi = new ApmTrendFieldItem(
                "avg(latency)", "latency_avg", "0", true, 2, "ms", true);
        ApmTrendViewConfig view = new ApmTrendViewConfig(
                "trend", "default-collector", "latency", "Latency Trend",
                "H", "host", "env=prod", List.of(fi), true, "5m",
                "time desc", "1h");
        ApmTrendRequest req = new ApmTrendRequest(
                7000L, view, 9001L, 333L, 1001L,
                "2026-06-10 10:00:00", "2026-06-10 11:00:00");
        adapter.showTrend(req);

        ArgumentCaptor<ShowTrendRequest> captor = ArgumentCaptor.forClass(ShowTrendRequest.class);
        verify(apmClient, times(1)).showTrend(captor.capture());
        ShowTrendRequest sdkReq = captor.getValue();

        assertThat(sdkReq.getXBusinessId()).isEqualTo(7000L);
        assertThat(sdkReq.getBody().getInstanceId()).isEqualTo(9001L);
        assertThat(sdkReq.getBody().getMonitorItemId()).isEqualTo(333L);
        assertThat(sdkReq.getBody().getEnvId()).isEqualTo(1001L);
        assertThat(sdkReq.getBody().getStartTime()).isEqualTo("2026-06-10 10:00:00");
        assertThat(sdkReq.getBody().getEndTime()).isEqualTo("2026-06-10 11:00:00");

        TrendView v = sdkReq.getBody().getViewConfig();
        assertThat(v.getViewType()).isEqualTo(TrendView.ViewTypeEnum.TREND);
        assertThat(v.getCollectorName()).isEqualTo("default-collector");
        assertThat(v.getMetricSet()).isEqualTo("latency");
        assertThat(v.getTitle()).isEqualTo("Latency Trend");
        assertThat(v.getTableDirection()).isEqualTo(TrendView.TableDirectionEnum.H);
        assertThat(v.getGroupBy()).isEqualTo("host");
        assertThat(v.getFilter()).isEqualTo("env=prod");
        assertThat(v.getSpan()).isTrue();
        assertThat(v.getSpanField()).isEqualTo("5m");
        assertThat(v.getOrderBy()).isEqualTo("time desc");
        assertThat(v.getLatest()).isEqualTo("1h");

        assertThat(v.getFieldItemList()).hasSize(1);
        var sdkFi = v.getFieldItemList().get(0);
        assertThat(sdkFi.getFunction()).isEqualTo("avg(latency)");
        assertThat(sdkFi.getAs()).isEqualTo("latency_avg");
        assertThat(sdkFi.getDefaultValue()).isEqualTo("0");
        assertThat(sdkFi.getTrace()).isTrue();
        assertThat(sdkFi.getPrecision()).isEqualTo(2);
        assertThat(sdkFi.getUnit()).isEqualTo("ms");
        assertThat(sdkFi.getVisible()).isTrue();
    }

    @Test
    @DisplayName("UT-A1b: null businessId falls back to huaweicloud.apm-business-id config")
    void ut01bBusinessIdFallback() {
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenReturn(new ShowTrendResponse().withLineList(List.of()).withLatestDataTime(0L));

        adapter.showTrend(minimalRequest(null));

        ArgumentCaptor<ShowTrendRequest> captor = ArgumentCaptor.forClass(ShowTrendRequest.class);
        verify(apmClient).showTrend(captor.capture());
        assertThat(captor.getValue().getXBusinessId()).isEqualTo(99999L);
    }

    @Test
    @DisplayName("UT-A1c: null fieldItemList passes through as null (not coerced to empty list)")
    void ut01cFieldItemListNullPassthrough() {
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenReturn(new ShowTrendResponse().withLineList(List.of()).withLatestDataTime(0L));

        adapter.showTrend(minimalRequest(7000L));

        ArgumentCaptor<ShowTrendRequest> captor = ArgumentCaptor.forClass(ShowTrendRequest.class);
        verify(apmClient).showTrend(captor.capture());
        assertThat(captor.getValue().getBody().getViewConfig().getFieldItemList()).isNull();
    }

    @Test
    @DisplayName("UT-A2a: HTTP 429 -> retried 3x then UPSTREAM_THROTTLED (retryable)")
    void ut02aThrottled() {
        AtomicInteger calls = new AtomicInteger();
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(429, "APM.0429", "throttled", "req-429");
                });
        assertThatThrownBy(() -> adapter.showTrend(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED)
                .matches(ex -> "req-429".equals(((SmartomException) ex).getUpstreamTraceId()));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-A2b: HTTP 401 -> no retry, UPSTREAM_AUTH_FAILED")
    void ut02bAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(401, "APIGW.0301", "unauthorized", "req-401");
                });
        assertThatThrownBy(() -> adapter.showTrend(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-A2c: HTTP 5xx -> retried 3x then UPSTREAM_ERROR")
    void ut02cServerError() {
        AtomicInteger calls = new AtomicInteger();
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ServerResponseException(503, "APM.5030", "unavailable", "req-503");
                });
        assertThatThrownBy(() -> adapter.showTrend(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-A2d: SDK timeout -> TIMEOUT")
    void ut02dTimeout() {
        when(apmClient.showTrend(any(ShowTrendRequest.class)))
                .thenThrow(new RequestTimeoutException("read timed out"));
        assertThatThrownBy(() -> adapter.showTrend(minimalRequest(7000L)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.TIMEOUT);
    }

    private ApmTrendRequest minimalRequest(Long businessId) {
        ApmTrendViewConfig view = new ApmTrendViewConfig(
                "trend", null, "latency", null,
                null, null, null, null, null, null, null, null);
        return new ApmTrendRequest(
                businessId, view, null, null, null,
                "2026-06-10 10:00:00", "2026-06-10 11:00:00");
    }
}
