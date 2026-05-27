/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsRequest;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsResponse;
import com.huaweicloud.sdk.ces.v1.model.MetaData;
import com.huaweicloud.sdk.ces.v1.model.MetricInfoList;
import com.huaweicloud.sdk.ces.v1.model.MetricsDimension;
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
 * Unit tests for {@link CesMetricsAdapterImpl}.
 *
 * <p>Covers UT-01/02/03/09/10/11/12/13/14/15 from the list_ces_metrics spec.
 * Parameter-validation cases (UT-04..08) belong to {@code CesMetricsServiceTest}.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class CesMetricsAdapterImplTest {

    private static final String RL = "ces-readonly";
    private static final String RETRY = "huaweicloud-retryable";

    private CesClient cesClient;
    private HuaweiCloudInvocation invocation;
    private CesMetricsAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        cesClient = mock(CesClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter(RL);

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException smartomEx && smartomEx.getErrorCode().isRetryable())
                .build());
        retryReg.retry(RETRY);

        invocation = new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper());
        adapter = new CesMetricsAdapterImpl(cesClient, invocation);
    }

    @Test
    @DisplayName("UT-01: defaults — SDK called with null filters, limit=100, order=DESC")
    void ut01DefaultsMapped() {
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenReturn(emptyResponse());

        CesListMetricsRequest req = new CesListMetricsRequest(
                null, null, null, null, null, null, null);
        adapter.listMetrics(req);

        ListMetricsRequest sdkReq = capture();
        assertThat(sdkReq.getNamespace()).isNull();
        assertThat(sdkReq.getMetricName()).isNull();
        assertThat(sdkReq.getDim0()).isNull();
        assertThat(sdkReq.getLimit()).isEqualTo(100);
        assertThat(sdkReq.getStart()).isNull();
        assertThat(sdkReq.getOrder()).isEqualTo(ListMetricsRequest.OrderEnum.DESC);
    }

    @Test
    @DisplayName("UT-02: namespace=SYS.ECS is passed to SDK")
    void ut02NamespacePassed() {
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenReturn(emptyResponse());

        adapter.listMetrics(new CesListMetricsRequest(
                "SYS.ECS", null, null, null, 50, null, "asc"));

        ListMetricsRequest sdkReq = capture();
        assertThat(sdkReq.getNamespace()).isEqualTo("SYS.ECS");
        assertThat(sdkReq.getLimit()).isEqualTo(50);
        assertThat(sdkReq.getOrder()).isEqualTo(ListMetricsRequest.OrderEnum.ASC);
    }

    @Test
    @DisplayName("UT-03: dim_name + dim_value -> SDK dim0 = 'name,value'")
    void ut03Dim0Concatenated() {
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenReturn(emptyResponse());

        adapter.listMetrics(new CesListMetricsRequest(
                null, null, "instance_id", "abc-123", null, null, null));

        ListMetricsRequest sdkReq = capture();
        assertThat(sdkReq.getDim0()).isEqualTo("instance_id,abc-123");
    }

    @Test
    @DisplayName("UT-09: SDK returns empty list -> metrics=[], has_more=false, total=0")
    void ut09EmptyResult() {
        ListMetricsResponse resp = new ListMetricsResponse()
                .withMetrics(List.of())
                .withMetaData(new MetaData().withCount(0).withTotal(0));
        when(cesClient.listMetrics(any(ListMetricsRequest.class))).thenReturn(resp);

        CesListMetricsResponse out = adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, null, null, null));

        assertThat(out.metrics()).isEmpty();
        assertThat(out.pagination().count()).isEqualTo(0);
        assertThat(out.pagination().total()).isEqualTo(0);
        assertThat(out.pagination().nextMarker()).isNull();
        assertThat(out.pagination().hasMore()).isFalse();
    }

    @Test
    @DisplayName("UT-10: marker present + count>0 -> has_more=true, marker passed through")
    void ut10HasMore() {
        MetricInfoList metric = new MetricInfoList()
                .withNamespace("SYS.ECS")
                .withMetricName("cpu_util")
                .withUnit("%")
                .withDimensions(List.of(new MetricsDimension().withName("instance_id").withValue("i-1")));
        ListMetricsResponse resp = new ListMetricsResponse()
                .withMetrics(List.of(metric))
                .withMetaData(new MetaData().withCount(100).withTotal(523).withMarker("next-marker-x"));
        when(cesClient.listMetrics(any(ListMetricsRequest.class))).thenReturn(resp);

        CesListMetricsResponse out = adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, 100, null, null));

        assertThat(out.pagination().hasMore()).isTrue();
        assertThat(out.pagination().nextMarker()).isEqualTo("next-marker-x");
        assertThat(out.pagination().count()).isEqualTo(100);
        assertThat(out.pagination().total()).isEqualTo(523);
    }

    @Test
    @DisplayName("UT-11: HTTP 429 -> retried 3x, then UPSTREAM_THROTTLED (retryable)")
    void ut11ThrottledRetried() {
        AtomicInteger calls = new AtomicInteger();
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(429, "CES.0429", "throttled", "req-429");
                });

        assertThatThrownBy(() -> adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED)
                .matches(ex -> "req-429".equals(((SmartomException) ex).getUpstreamTraceId()));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-12: HTTP 401 -> no retry, UPSTREAM_AUTH_FAILED (non-retryable)")
    void ut12AuthFailedNoRetry() {
        AtomicInteger calls = new AtomicInteger();
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(401, "APIGW.0301", "unauthorized", "req-401");
                });

        assertThatThrownBy(() -> adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-13: HTTP 503 -> retried 3x then UPSTREAM_ERROR")
    void ut13ServerErrorRetried() {
        AtomicInteger calls = new AtomicInteger();
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ServerResponseException(503, "CES.5030", "unavailable", "req-503");
                });

        assertThatThrownBy(() -> adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-14: SDK timeout -> TIMEOUT (retryable)")
    void ut14Timeout() {
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenThrow(new RequestTimeoutException("read timed out"));

        assertThatThrownBy(() -> adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.TIMEOUT);
    }

    @Test
    @DisplayName("UT-15: unit field is always carried through")
    void ut15UnitPreserved() {
        MetricInfoList metric = new MetricInfoList()
                .withNamespace("SYS.ECS")
                .withMetricName("disk_read_bytes_rate")
                .withUnit("Byte/s")
                .withDimensions(List.of());
        when(cesClient.listMetrics(any(ListMetricsRequest.class)))
                .thenReturn(new ListMetricsResponse()
                        .withMetrics(List.of(metric))
                        .withMetaData(new MetaData().withCount(1).withTotal(1)));

        CesListMetricsResponse out = adapter.listMetrics(
                new CesListMetricsRequest(null, null, null, null, null, null, null));
        assertThat(out.metrics()).hasSize(1);
        assertThat(out.metrics().get(0).unit()).isEqualTo("Byte/s");
    }

    // ---- helpers ----

    private ListMetricsResponse emptyResponse() {
        return new ListMetricsResponse()
                .withMetrics(List.of())
                .withMetaData(new MetaData().withCount(0).withTotal(0));
    }

    private ListMetricsRequest capture() {
        ArgumentCaptor<ListMetricsRequest> captor = ArgumentCaptor.forClass(ListMetricsRequest.class);
        verify(cesClient, times(1)).listMetrics(captor.capture());
        return captor.getValue();
    }
}
