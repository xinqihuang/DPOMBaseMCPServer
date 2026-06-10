/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.contract;

import com.huawei.smartom.agentic.adapter.ces.CesNotificationMaskAdapterImpl;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMask;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskHierarchicalValue;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskMetricExtraInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskPolicy;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskResourceCategory;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskProductMetric;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.huaweicloud.sdk.ces.v2.CesClient;
import com.huaweicloud.sdk.ces.v2.model.ListNotificationMasksRequest;
import com.huaweicloud.sdk.ces.v2.model.ListNotificationMasksResponse;

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
 * CES v2 {@code ListNotificationMasks} 接口的 schema 契约测试。
 *
 * <p>T19 PR-2 重点：之前 {@link CesNotificationMask} 丢了 5 个 SDK 字段
 * （{@code resourceType} / {@code resources} / {@code createTime} / {@code updateTime} /
 * {@code policies}），本测试样本同时覆盖深嵌套 {@link CesNotificationMaskPolicy}
 * （含 {@link CesNotificationMaskMetricExtraInfo} 与 {@link CesNotificationMaskHierarchicalValue}）。
 *
 * <p>删除任一新增字段会让对应 record accessor 消失，从而让本测试编译失败。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class CesListNotificationMasksContractTest {

    private CesClient cesV2Client;
    private CesNotificationMaskAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        cesV2Client = mock(CesClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter("ces-readonly");
        rlReg.rateLimiter("ces-write");

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry("huaweicloud-retryable");

        adapter = new CesNotificationMaskAdapterImpl(cesV2Client,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()));
    }

    @Test
    @DisplayName("Sample JSON deserialises into SDK response, all 20 mask fields populated")
    void sdkDeserialisationCovers20Fields() throws IOException {
        ListNotificationMasksResponse sdkResp = loadSample();
        assertThat(sdkResp.getCount()).isEqualTo(1);
        assertThat(sdkResp.getNotificationMasks()).hasSize(1);

        var mask = sdkResp.getNotificationMasks().get(0);
        assertThat(mask.getNotificationMaskId()).isNotBlank();
        assertThat(mask.getMaskName()).isNotBlank();
        assertThat(mask.getRelationType().getValue()).isEqualTo("ALARM_RULE");
        assertThat(mask.getRelationId()).isNotBlank();
        assertThat(mask.getResourceType().getValue()).isEqualTo("PRODUCT");
        assertThat(mask.getMetricNames()).contains("cpu_util", "mem_util");
        assertThat(mask.getProductMetrics()).hasSize(1);
        assertThat(mask.getResourceLevel().getValue()).isEqualTo("product");
        assertThat(mask.getProductName()).isEqualTo("ECS");
        assertThat(mask.getResources()).hasSize(1);
        assertThat(mask.getMaskStatus().getValue()).isEqualTo("MASK_EFFECTIVE");
        assertThat(mask.getMaskType().getValue()).isEqualTo("CYCLE_TIME");
        assertThat(mask.getCreateTime()).isEqualTo(1718000000000L);
        assertThat(mask.getUpdateTime()).isEqualTo(1718000600000L);
        assertThat(mask.getStartDate()).isNotNull();
        assertThat(mask.getStartTime()).isEqualTo("10:00:00");
        assertThat(mask.getEndDate()).isNotNull();
        assertThat(mask.getEndTime()).isEqualTo("12:00:00");
        assertThat(mask.getEffectiveTimezone()).isEqualTo("GMT+08:00");
        assertThat(mask.getPolicies()).hasSize(1);
    }

    @Test
    @DisplayName("Adapter maps every SDK field into the lossless DTO (deleting any DTO field fails compilation)")
    void adapterMapsLosslessly() throws IOException {
        ListNotificationMasksResponse sdkResp = loadSample();
        when(cesV2Client.listNotificationMasks(any(ListNotificationMasksRequest.class)))
                .thenReturn(sdkResp);

        CesListNotificationMasksResponse out = adapter.listNotificationMasks(
                new CesListNotificationMasksRequest(
                        0, 100, null, null,
                        null, null, null, null, null, null, null, null, null, null));

        assertThat(out.count()).isEqualTo(1);
        assertThat(out.notificationMasks()).hasSize(1);

        CesNotificationMask mask = out.notificationMasks().get(0);

        // ---- 15 legacy fields ----
        assertThat(mask.notificationMaskId()).isEqualTo("nm123456789012345678zzzz");
        assertThat(mask.maskName()).isEqualTo("weekly-window-mask");
        assertThat(mask.relationType()).isEqualTo("ALARM_RULE");
        assertThat(mask.relationId()).isEqualTo("al1604473987569z6n6nkpm1");
        assertThat(mask.metricNames()).containsExactly("cpu_util", "mem_util");
        assertThat(mask.productMetrics()).hasSize(1);
        NotificationMaskProductMetric pm = mask.productMetrics().get(0);
        assertThat(pm.dimensionName()).isEqualTo("instance_id");
        assertThat(pm.metricName()).isEqualTo("cpu_util");
        assertThat(mask.resourceLevel()).isEqualTo("product");
        assertThat(mask.productName()).isEqualTo("ECS");
        assertThat(mask.maskStatus()).isEqualTo("MASK_EFFECTIVE");
        assertThat(mask.maskType()).isEqualTo("CYCLE_TIME");
        assertThat(mask.startDate()).isEqualTo("2024-02-11");
        assertThat(mask.startTime()).isEqualTo("10:00:00");
        assertThat(mask.endDate()).isEqualTo("2024-02-12");
        assertThat(mask.endTime()).isEqualTo("12:00:00");
        assertThat(mask.effectiveTimezone()).isEqualTo("GMT+08:00");

        // ---- 5 newly-added fields (PR-2) ----
        assertThat(mask.resourceType()).isEqualTo("PRODUCT");
        assertThat(mask.createTime()).isEqualTo(1718000000000L);
        assertThat(mask.updateTime()).isEqualTo(1718000600000L);

        assertThat(mask.resources()).hasSize(1);
        CesNotificationMaskResourceCategory rc = mask.resources().get(0);
        assertThat(rc.namespace()).isEqualTo("SYS.ECS");
        assertThat(rc.dimensionNames()).containsExactly("instance_id");

        assertThat(mask.policies()).hasSize(1);
        CesNotificationMaskPolicy policy = mask.policies().get(0);
        assertThat(policy.alarmPolicyId()).isEqualTo("ap12345678901234567890xx");
        assertThat(policy.metricName()).isEqualTo("cpu_util");
        assertThat(policy.period()).isEqualTo(300);
        assertThat(policy.filter()).isEqualTo("average");
        assertThat(policy.comparisonOperator()).isEqualTo(">=");
        assertThat(policy.value()).isEqualTo(80.0);
        assertThat(policy.unit()).isEqualTo("%");
        assertThat(policy.count()).isEqualTo(3);
        assertThat(policy.type()).isEqualTo("static");
        assertThat(policy.suppressDuration()).isEqualTo(3600);
        assertThat(policy.alarmLevel()).isEqualTo(2);
        assertThat(policy.selectedUnit()).isEqualTo("%");

        CesNotificationMaskMetricExtraInfo extra = policy.extraInfo();
        assertThat(extra).isNotNull();
        assertThat(extra.originMetricName()).isEqualTo("cpu_util");
        assertThat(extra.metricPrefix()).isEmpty();
        assertThat(extra.customProcName()).isEmpty();
        assertThat(extra.metricType()).isEqualTo("system");

        CesNotificationMaskHierarchicalValue hv = policy.hierarchicalValue();
        assertThat(hv).isNotNull();
        assertThat(hv.critical()).isEqualTo(95.0);
        assertThat(hv.major()).isEqualTo(90.0);
        assertThat(hv.minor()).isEqualTo(85.0);
        assertThat(hv.info()).isEqualTo(80.0);
    }

    private ListNotificationMasksResponse loadSample() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addModule(new JavaTimeModule())
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/ces/list-notification-masks-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListNotificationMasksResponse.class);
        }
    }
}
