/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmTraceAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSpanEvent;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowClobDetailRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowClobDetailResponse;
import com.huaweicloud.sdk.apm.v1.model.ShowEventDetailRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowEventDetailResponse;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * APM v1 {@code ShowEventDetail} 与 {@code ShowClobDetail} 接口的 schema 契约测试（T29）。
 *
 * <p>事件详情复用 {@link ApmSpanEvent}（全量覆盖见 {@code ApmShowTraceEventsContractTest}），
 * 本测试侧重请求侧装配（四 query 参数 / clob 的 header+body 与 business 回落）与响应根字段。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmShowEventDetailContractTest {

    private ApmClient apmClient;
    private ApmTraceAdapterImpl adapter;

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

        adapter = new ApmTraceAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                properties);
    }

    @Test
    @DisplayName("ShowEventDetail: four query params marshalled, event_info mapped")
    void eventDetailMapped() throws IOException {
        when(apmClient.showEventDetail(any(ShowEventDetailRequest.class)))
                .thenReturn(loadEventDetailSample());

        ApmEventDetailResponse out = adapter.showEventDetail(
                new ApmEventDetailRequest("trace-abc", "span-1", "evt-100", 1306682L));

        ArgumentCaptor<ShowEventDetailRequest> captor =
                ArgumentCaptor.forClass(ShowEventDetailRequest.class);
        verify(apmClient).showEventDetail(captor.capture());
        ShowEventDetailRequest sdk = captor.getValue();
        assertThat(sdk.getTraceId()).isEqualTo("trace-abc");
        assertThat(sdk.getSpanId()).isEqualTo("span-1");
        assertThat(sdk.getEventId()).isEqualTo("evt-100");
        assertThat(sdk.getEnvId()).isEqualTo(1306682L);

        ApmSpanEvent info = out.eventInfo();
        assertThat(info.eventId()).isEqualTo("evt-100");
        assertThat(info.envId()).isEqualTo(1306682L);
        assertThat(info.hasError()).isTrue();
        assertThat(info.tags())
                .containsEntry("sql_clob_id", "clob-778")
                .containsEntry("exceptionMessage", "Statement timed out after 2s");
    }

    @Test
    @DisplayName("ShowClobDetail: header business fallback + body env_id/clob_id, clob_string mapped")
    void clobDetailMapped() throws IOException {
        when(apmClient.showClobDetail(any(ShowClobDetailRequest.class)))
                .thenReturn(loadClobSample());

        ApmClobDetailResponse out = adapter.showClobDetail(
                new ApmClobDetailRequest(null, 1306682L, "clob-778"));

        ArgumentCaptor<ShowClobDetailRequest> captor =
                ArgumentCaptor.forClass(ShowClobDetailRequest.class);
        verify(apmClient).showClobDetail(captor.capture());
        ShowClobDetailRequest sdk = captor.getValue();
        assertThat(sdk.getXBusinessId()).isEqualTo(99999L);
        assertThat(sdk.getBody().getEnvId()).isEqualTo(1306682L);
        assertThat(sdk.getBody().getClobId()).isEqualTo("clob-778");

        assertThat(out.clobString())
                .startsWith("java.sql.SQLTimeoutException")
                .contains("OrderDao.java:88");
    }

    private ShowEventDetailResponse loadEventDetailSample() throws IOException {
        return load("/sdk-samples/apm/show-event-detail-response.json", ShowEventDetailResponse.class);
    }

    private ShowClobDetailResponse loadClobSample() throws IOException {
        return load("/sdk-samples/apm/show-clob-detail-response.json", ShowClobDetailResponse.class);
    }

    private <T> T load(String resource, Class<T> type) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("sample JSON must exist: %s", resource).isNotNull();
            return mapper.readValue(in, type);
        }
    }
}
