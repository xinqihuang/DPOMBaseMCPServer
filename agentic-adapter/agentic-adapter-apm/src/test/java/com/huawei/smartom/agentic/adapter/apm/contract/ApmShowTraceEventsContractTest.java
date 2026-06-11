/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.contract;

import com.huawei.smartom.agentic.adapter.apm.ApmTraceAdapterImpl;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmDiscardInfo;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSpanEvent;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ShowTraceEventsRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowTraceEventsResponse;

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
 * APM v1 {@code ShowTraceEvents} 接口的 schema 契约测试（T29）。
 *
 * <p>覆盖 {@link ApmSpanEvent} 全部 38 字段（含混合命名键 {@code next_spanId}）与
 * {@link ApmDiscardInfo} 全部 3 字段（含驼峰键 {@code totalTime}），漂移即 fail。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmShowTraceEventsContractTest {

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

        adapter = new ApmTraceAdapterImpl(apmClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()),
                new HuaweiCloudProperties());
    }

    @Test
    @DisplayName("Adapter maps ShowTraceEvents sample losslessly (38 event fields + discard)")
    void adapterMapsLosslessly() throws IOException {
        when(apmClient.showTraceEvents(any(ShowTraceEventsRequest.class))).thenReturn(loadSample());

        ApmTraceEventsResponse out = adapter.showTraceEvents("trace-abc");

        ArgumentCaptor<ShowTraceEventsRequest> captor =
                ArgumentCaptor.forClass(ShowTraceEventsRequest.class);
        verify(apmClient).showTraceEvents(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-abc");

        assertThat(out.spanEventList()).hasSize(2);
        ApmSpanEvent full = out.spanEventList().get(0);
        assertThat(full.envName()).isEqualTo("prod");
        assertThat(full.appName()).isEqualTo("order-svc");
        assertThat(full.indent()).isEqualTo(2);
        assertThat(full.region()).isEqualTo("cn-north-4");
        assertThat(full.hostName()).isEqualTo("node-1");
        assertThat(full.ipAddress()).isEqualTo("192.168.1.10");
        assertThat(full.instanceName()).isEqualTo("order-svc-0");
        assertThat(full.eventId()).isEqualTo("evt-100");
        assertThat(full.nextSpanId()).isEqualTo("span-2");
        assertThat(full.sourceEventId()).isEqualTo("evt-099");
        assertThat(full.method()).isEqualTo("querySql");
        assertThat(full.childrenEventCount()).isEqualTo(0);
        assertThat(full.argument()).startsWith("SELECT");
        assertThat(full.attachment()).containsEntry("db_instance", "rds-1");
        assertThat(full.globalTraceId()).isEqualTo("gtid-1");
        assertThat(full.globalPath()).isEqualTo("0.1.2");
        assertThat(full.traceId()).isEqualTo("trace-abc");
        assertThat(full.spanId()).isEqualTo("span-1");
        assertThat(full.envId()).isEqualTo(1306682L);
        assertThat(full.instanceId()).isEqualTo(88001L);
        assertThat(full.appId()).isEqualTo(555001L);
        assertThat(full.bizId()).isEqualTo(7000L);
        assertThat(full.domainId()).isEqualTo(12345);
        assertThat(full.source()).isEqualTo("MYSQL");
        assertThat(full.realSource()).isEqualTo("MYSQL_JDBC");
        assertThat(full.startTime()).isEqualTo(1718000000123L);
        assertThat(full.timeUsed()).isEqualTo(2456L);
        assertThat(full.code()).isEqualTo(500);
        assertThat(full.className()).isEqualTo("com.example.OrderDao");
        assertThat(full.isAsync()).isFalse();
        assertThat(full.tags())
                .containsEntry("exceptionType", "java.sql.SQLTimeoutException")
                .containsEntry("stacktrace_clob_id", "clob-777");
        assertThat(full.hasError()).isTrue();
        assertThat(full.errorReasons()).isEqualTo("SQLTimeoutException");
        assertThat(full.type()).isEqualTo("SQL");
        assertThat(full.httpMethod()).isEqualTo("POST");
        assertThat(full.bizCode()).isEqualTo("ORDER_QUERY");
        assertThat(full.id()).isEqualTo("evt-100");

        ApmDiscardInfo discard = full.discard().get(0);
        assertThat(discard.type()).isEqualTo("sql");
        assertThat(discard.count()).isEqualTo(3);
        assertThat(discard.totalTime()).isEqualTo(1200L);

        ApmSpanEvent minimal = out.spanEventList().get(1);
        assertThat(minimal.eventId()).isEqualTo("evt-101");
        assertThat(minimal.discard()).isNull();
        assertThat(minimal.tags()).isNull();
    }

    private ShowTraceEventsResponse loadSample() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/apm/show-trace-events-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ShowTraceEventsResponse.class);
        }
    }
}
