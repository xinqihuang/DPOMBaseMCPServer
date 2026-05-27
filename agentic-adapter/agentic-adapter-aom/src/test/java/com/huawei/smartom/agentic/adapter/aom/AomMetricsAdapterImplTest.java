/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.Dimension;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsResponse;
import com.huaweicloud.sdk.aom.v2.model.MetaDataSeries;
import com.huaweicloud.sdk.aom.v2.model.MetricItemResultAPI;
import com.huaweicloud.sdk.aom.v2.model.QueryMetricItemOptionParam;
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
import java.util.Collections;
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
 * Unit tests for {@link AomMetricsAdapterImpl}, covering adapter routing, response mapping,
 * and error handling (spec UT IDs: UT-01 to UT-04, UT-12 to UT-20).
 *
 * <p>Input validation tests (UT-05 to UT-11) live in {@code AomMetricsServiceTest} in the
 * monitoring module, because validation is enforced by the service layer.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class AomMetricsAdapterImplTest {

    private static final String RL = "aom-readonly";
    private static final String RETRY = "huaweicloud-retryable";

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
        rlReg.rateLimiter(RL);

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(t ->
                        t instanceof SmartomException s && s.getErrorCode().isRetryable())
                .build());
        retryReg.retry(RETRY);

        HuaweiCloudInvocation invocation = new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper());
        adapter = new AomMetricsAdapterImpl(aomClient, invocation);
    }

    // ---- UT-01 to UT-04: adapter routing / field mapping ----

    @Test
    @DisplayName("UT-01: namespace-only — body has one QueryMetricItemOptionParam with PAAS.CONTAINER enum")
    void ut01NamespaceOnlyMapped() {
        when(aomClient.listMetricItems(any())).thenReturn(emptyResponse());

        adapter.listMetrics(new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null));

        ListMetricItemsRequest sdkReq = captureRequest();
        assertThat(sdkReq.getType()).isNull();
        assertThat(sdkReq.getBody().getMetricItems()).hasSize(1);
        assertThat(sdkReq.getBody().getMetricItems().get(0).getNamespace())
                .isEqualTo(QueryMetricItemOptionParam.NamespaceEnum.PAAS_CONTAINER);
        assertThat(sdkReq.getBody().getInventoryId()).isNull();
    }

    @Test
    @DisplayName("UT-02: namespace + metric_name + dimensions — all mapped to metricItems[0]")
    void ut02FullNamespaceQueryMapped() {
        when(aomClient.listMetricItems(any())).thenReturn(emptyResponse());

        List<AomMetricDimension> dims = List.of(new AomMetricDimension("appName", "myApp"));
        adapter.listMetrics(new AomListMetricsRequest(
                "PAAS.CONTAINER", "aom_cpu", dims, null, 50, 10));

        ListMetricItemsRequest sdkReq = captureRequest();
        QueryMetricItemOptionParam item = sdkReq.getBody().getMetricItems().get(0);
        assertThat(item.getNamespace())
                .isEqualTo(QueryMetricItemOptionParam.NamespaceEnum.PAAS_CONTAINER);
        assertThat(item.getMetricName()).isEqualTo("aom_cpu");
        assertThat(item.getDimensions()).hasSize(1);
        assertThat(item.getDimensions().get(0).getName()).isEqualTo("appName");
        assertThat(item.getDimensions().get(0).getValue()).isEqualTo("myApp");
    }

    @Test
    @DisplayName("UT-03: inventory_id only — type=inventory, body.inventoryId set, metricItems null")
    void ut03InventoryIdOnlyPath() {
        when(aomClient.listMetricItems(any())).thenReturn(emptyResponse());

        adapter.listMetrics(new AomListMetricsRequest(null, null, null, "host_abc-123", null, null));

        ListMetricItemsRequest sdkReq = captureRequest();
        assertThat(sdkReq.getType()).isEqualTo("inventory");
        assertThat(sdkReq.getBody().getInventoryId()).isEqualTo("host_abc-123");
        assertThat(sdkReq.getBody().getMetricItems()).isNull();
    }

    @Test
    @DisplayName("UT-04: both namespace + inventory_id — inventory_id wins, WARN written, metricItems null")
    void ut04InventoryIdWinsOverNamespace() {
        when(aomClient.listMetricItems(any())).thenReturn(emptyResponse());

        adapter.listMetrics(new AomListMetricsRequest(
                "PAAS.CONTAINER", null, null, "host_abc-123", null, null));

        ListMetricItemsRequest sdkReq = captureRequest();
        assertThat(sdkReq.getType()).isEqualTo("inventory");
        assertThat(sdkReq.getBody().getInventoryId()).isEqualTo("host_abc-123");
        assertThat(sdkReq.getBody().getMetricItems()).isNull();
    }

    // ---- UT-12 to UT-14: response mapping ----

    @Test
    @DisplayName("UT-12: empty metrics from SDK — pagination.count=0, has_more=false")
    void ut12EmptyMetricsResult() {
        when(aomClient.listMetricItems(any())).thenReturn(
                new ListMetricItemsResponse()
                        .withMetrics(Collections.emptyList())
                        .withMetaData(new MetaDataSeries().withCount(0).withTotal(0)));

        AomListMetricsResponse out = adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null));

        assertThat(out.metrics()).isEmpty();
        assertThat(out.pagination().count()).isEqualTo(0);
        assertThat(out.pagination().hasMore()).isFalse();
        assertThat(out.pagination().nextToken()).isNull();
    }

    @Test
    @DisplayName("UT-13: next_token != null + count > 0 — has_more=true, next_token passed through")
    void ut13HasMoreWhenNextTokenPresent() {
        MetricItemResultAPI m = new MetricItemResultAPI()
                .withNamespace("PAAS.CONTAINER")
                .withMetricName("aom_cpu")
                .withUnit("Percent")
                .withDimensions(List.of(new Dimension().withName("appName").withValue("app1")));
        when(aomClient.listMetricItems(any())).thenReturn(
                new ListMetricItemsResponse()
                        .withMetrics(List.of(m))
                        .withMetaData(new MetaDataSeries().withCount(100).withTotal(523).withNextToken(100)));

        AomListMetricsResponse out = adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, 100, null));

        assertThat(out.pagination().hasMore()).isTrue();
        assertThat(out.pagination().nextToken()).isEqualTo(100);
        assertThat(out.pagination().count()).isEqualTo(100);
        assertThat(out.pagination().total()).isEqualTo(523);
    }

    @Test
    @DisplayName("UT-14: next_token=null — has_more=false")
    void ut14NoNextTokenHasMoreFalse() {
        MetricItemResultAPI m = new MetricItemResultAPI()
                .withNamespace("PAAS.NODE").withMetricName("aom_mem").withUnit("Percent");
        when(aomClient.listMetricItems(any())).thenReturn(
                new ListMetricItemsResponse()
                        .withMetrics(List.of(m))
                        .withMetaData(new MetaDataSeries().withCount(1).withTotal(1)));

        AomListMetricsResponse out = adapter.listMetrics(
                new AomListMetricsRequest("PAAS.NODE", null, null, null, null, null));

        assertThat(out.pagination().hasMore()).isFalse();
        assertThat(out.pagination().nextToken()).isNull();
    }

    // ---- UT-15 to UT-18: error handling ----

    @Test
    @DisplayName("UT-15: HTTP 429 — retried 3 times, then UPSTREAM_THROTTLED + trace_id")
    void ut15ThrottledRetried3Times() {
        AtomicInteger calls = new AtomicInteger();
        when(aomClient.listMetricItems(any()))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(429, "AOM.0429", "throttled", "req-xyz");
                });

        assertThatThrownBy(() -> adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(e -> ((SmartomException) e).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED)
                .matches(e -> "req-xyz".equals(((SmartomException) e).getUpstreamTraceId()));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-16: HTTP 401 — no retry, UPSTREAM_AUTH_FAILED")
    void ut16AuthFailedNoRetry() {
        AtomicInteger calls = new AtomicInteger();
        when(aomClient.listMetricItems(any()))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(401, "AOM.0401", "unauthorized", "req-401");
                });

        assertThatThrownBy(() -> adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(e -> ((SmartomException) e).getErrorCode() == ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-17: HTTP 503 — retried 3 times, UPSTREAM_ERROR")
    void ut17ServerErrorRetried() {
        AtomicInteger calls = new AtomicInteger();
        when(aomClient.listMetricItems(any()))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ServerResponseException(503, "AOM.5030", "unavailable", "req-503");
                });

        assertThatThrownBy(() -> adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(e -> ((SmartomException) e).getErrorCode() == ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-18: RequestTimeoutException — TIMEOUT (retryable)")
    void ut18TimeoutMappedCorrectly() {
        when(aomClient.listMetricItems(any()))
                .thenThrow(new RequestTimeoutException("read timed out"));

        assertThatThrownBy(() -> adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(e -> ((SmartomException) e).getErrorCode() == ErrorCode.TIMEOUT);
    }

    // ---- UT-19 to UT-20: SDK type mapping details ----

    @Test
    @DisplayName("UT-19: limit and start are passed to SDK as String.valueOf(int)")
    void ut19LimitStartPassedAsString() {
        when(aomClient.listMetricItems(any())).thenReturn(emptyResponse());

        adapter.listMetrics(new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, 50, 10));

        ListMetricItemsRequest sdkReq = captureRequest();
        assertThat(sdkReq.getLimit()).isEqualTo("50");
        assertThat(sdkReq.getStart()).isEqualTo("10");
    }

    @Test
    @DisplayName("UT-20: dimension_value_hash is carried through from SDK response")
    void ut20DimensionValueHashPassedThrough() {
        MetricItemResultAPI metric = new MetricItemResultAPI()
                .withNamespace("PAAS.CONTAINER")
                .withMetricName("aom_cpu")
                .withUnit("Percent")
                .withDimensions(Collections.emptyList())
                .withDimensionvaluehash("abc123hash");
        when(aomClient.listMetricItems(any())).thenReturn(
                new ListMetricItemsResponse()
                        .withMetrics(List.of(metric))
                        .withMetaData(new MetaDataSeries().withCount(1).withTotal(1)));

        AomListMetricsResponse out = adapter.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, null));

        assertThat(out.metrics()).hasSize(1);
        assertThat(out.metrics().get(0).dimensionValueHash()).isEqualTo("abc123hash");
    }

    // ---- helpers ----

    private ListMetricItemsResponse emptyResponse() {
        return new ListMetricItemsResponse()
                .withMetrics(Collections.emptyList())
                .withMetaData(new MetaDataSeries().withCount(0).withTotal(0));
    }

    private ListMetricItemsRequest captureRequest() {
        ArgumentCaptor<ListMetricItemsRequest> captor =
                ArgumentCaptor.forClass(ListMetricItemsRequest.class);
        verify(aomClient, times(1)).listMetricItems(captor.capture());
        return captor.getValue();
    }
}
