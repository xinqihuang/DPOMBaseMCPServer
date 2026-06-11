/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.contract;

import com.huawei.smartom.agentic.adapter.aom.AomEventAdapterImpl;
import com.huawei.smartom.agentic.adapter.aom.dto.AomAlertType;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEvent;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEventMetadataRelation;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.ListEventsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListEventsResponse;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AOM v2 {@code ListEvents} 接口的 schema 契约测试（T27）。
 *
 * <p>把 SDK 样本反序列化，经 adapter 映射，断言 {@link AomEvent} 全部 11 个字段与
 * {@code page_info} 3 个字段无损覆盖；同时校验请求侧 query/body 字段被正确装配。
 * 删除任一 DTO 字段会编译失败，字段漂移会断言失败。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomListEventsContractTest {

    private AomClient aomClient;
    private AomEventAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        aomClient = mock(AomClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter("aom-readonly");

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry("huaweicloud-retryable");

        adapter = new AomEventAdapterImpl(aomClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()));
    }

    @Test
    @DisplayName("Adapter maps ListEvents sample losslessly (11 event fields + page_info)")
    void adapterMapsLosslessly() throws IOException {
        ListEventsResponse sdkResp = loadSample();
        when(aomClient.listEvents(any(ListEventsRequest.class))).thenReturn(sdkResp);

        AomListEventsResponse out = adapter.listEvents(fullRequest());

        assertThat(out.events()).hasSize(2);

        AomEvent full = out.events().get(0);
        assertThat(full.id()).isEqualTo("evt-7f3a9c10");
        assertThat(full.eventSn()).isEqualTo("sn-20260611-0001");
        assertThat(full.startsAt()).isEqualTo(1718000000000L);
        assertThat(full.endsAt()).isEqualTo(1718000600000L);
        assertThat(full.arrivesAt()).isEqualTo(1718000001000L);
        assertThat(full.timeout()).isEqualTo(60000L);
        assertThat(full.enterpriseProjectId()).isEqualTo("0");
        assertThat(full.metadata())
                .containsEntry("event_severity", "Critical")
                .containsEntry("event_type", "alarm")
                .containsEntry("resource_id", "pod-order-svc-0")
                .containsEntry("resource_provider", "CCE")
                .containsEntry("namespace", "default");
        assertThat(full.annotations())
                .containsEntry("message", "container cpu usage exceeds 90% for 5 minutes")
                .containsEntry("alarm_probableCause_zh_cn", "容器 CPU 使用率过高");
        assertThat(full.attachRule())
                .containsEntry("name", "cpu-high-rule")
                .containsEntry("id", 42);
        assertThat(full.policy())
                .containsEntry("name", "default-policy")
                .containsEntry("level", 1);

        AomEvent minimal = out.events().get(1);
        assertThat(minimal.id()).isEqualTo("evt-minimal");
        assertThat(minimal.eventSn()).isNull();
        assertThat(minimal.metadata()).isNull();

        assertThat(out.pageInfo().currentCount()).isEqualTo(2);
        assertThat(out.pageInfo().previousMarker()).isEqualTo("0");
        assertThat(out.pageInfo().nextMarker()).isEqualTo("2");
    }

    @Test
    @DisplayName("Request DTO is marshalled into SDK query params and body")
    void requestMarshalledIntoSdkRequest() throws IOException {
        when(aomClient.listEvents(any(ListEventsRequest.class))).thenReturn(loadSample());

        adapter.listEvents(fullRequest());

        ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
        verify(aomClient).listEvents(captor.capture());
        ListEventsRequest sdk = captor.getValue();
        assertThat(sdk.getType()).isEqualTo(ListEventsRequest.TypeEnum.ACTIVE_ALERT);
        assertThat(sdk.getLimit()).isEqualTo(100);
        assertThat(sdk.getMarker()).isEqualTo("0");
        assertThat(sdk.getBody().getTimeRange()).isEqualTo("-1.-1.60");
        assertThat(sdk.getBody().getStep()).isEqualTo(60000L);
        assertThat(sdk.getBody().getSearch()).isEqualTo("cpu");
        assertThat(sdk.getBody().getSort().getOrderBy()).containsExactly("starts_at");
        assertThat(sdk.getBody().getSort().getOrder().getValue()).isEqualTo("desc");
        assertThat(sdk.getBody().getMetadataRelation()).hasSize(1);
        assertThat(sdk.getBody().getMetadataRelation().get(0).getKey()).isEqualTo("event_severity");
        assertThat(sdk.getBody().getMetadataRelation().get(0).getValue()).containsExactly("Critical");
        assertThat(sdk.getBody().getMetadataRelation().get(0).getRelation().getValue()).isEqualTo("AND");
    }

    private static AomListEventsRequest fullRequest() {
        return new AomListEventsRequest(
                AomAlertType.ACTIVE_ALERT, "-1.-1.60", 60000L, "cpu",
                List.of("starts_at"), "desc",
                List.of(new AomEventMetadataRelation("event_severity", List.of("Critical"), "AND")),
                100, "0");
    }

    private ListEventsResponse loadSample() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/aom/list-events-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListEventsResponse.class);
        }
    }
}
