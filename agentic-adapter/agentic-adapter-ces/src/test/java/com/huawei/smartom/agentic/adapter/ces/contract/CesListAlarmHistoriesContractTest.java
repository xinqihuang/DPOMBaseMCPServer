/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.contract;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapterImpl;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmAction;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmAdditionalInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmCondition;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmDataPoint;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmHistory;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmMetric;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v2.model.ListAlarmHistoriesRequest;
import com.huaweicloud.sdk.ces.v2.model.ListAlarmHistoriesResponse;

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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CES v2 {@code ListAlarmHistories} 接口的 schema 契约测试。
 *
 * <p>T19 PR-1 的关键防线：把华为云官方文档的样本 JSON 落到资源目录，
 * 反序列化为 v2 SDK 的 {@code ListAlarmHistoriesResponse}，通过 adapter 完成到
 * {@link CesAlarmHistory} 的映射，再逐字段断言。
 *
 * <p>核心保证：**如果从 {@link CesAlarmHistory} 删任一字段，测试必须变红**（要么编译失败，
 * 要么 record canonical constructor / accessor 缺失导致断言失败）。
 *
 * <p>SDK 升级（例如从 3.1.177 → 3.1.194 引入 {@code mask_status}）后，本测试不会自动
 * 验证新字段，但已有字段一旦漂移立刻可见。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class CesListAlarmHistoriesContractTest {

    private static final String RL = "ces-readonly";
    private static final String RETRY = "huaweicloud-retryable";

    private com.huaweicloud.sdk.ces.v2.CesClient cesV2Client;
    private CesMetricsAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        CesClient cesV1Client = mock(CesClient.class);
        cesV2Client = mock(com.huaweicloud.sdk.ces.v2.CesClient.class);

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
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry(RETRY);

        HuaweiCloudInvocation invocation =
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper());
        adapter = new CesMetricsAdapterImpl(cesV1Client, cesV2Client, invocation);
    }

    @Test
    @DisplayName("Sample JSON deserialises into SDK ListAlarmHistoriesResponse with all fields populated")
    void sampleJsonDeserialisesIntoSdk() throws IOException {
        ListAlarmHistoriesResponse sdkResp = loadSampleResponse();

        assertThat(sdkResp.getCount()).isEqualTo(103);
        assertThat(sdkResp.getAlarmHistories()).hasSize(1);

        var item = sdkResp.getAlarmHistories().get(0);
        assertThat(item.getAlarmId()).isEqualTo("al1604473987569z6n6nkpm1");
        assertThat(item.getRecordId()).isEqualTo("ah251222T092004SAD2yARym");
        assertThat(item.getName()).isEqualTo("TC_CES_FunctionBaseline_Alarm_008");
        assertThat(item.getStatus().getValue()).isEqualTo("alarm");
        assertThat(item.getLevel().getValue()).isEqualTo(2);
        assertThat(item.getType().getValue()).isEqualTo("ALL_INSTANCE");
        assertThat(item.getActionEnabled()).isFalse();

        assertThat(item.getBeginTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T05:48:08+08:00"));
        assertThat(item.getEndTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00"));
        assertThat(item.getFirstAlarmTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T06:48:08+08:00"));
        assertThat(item.getLastAlarmTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00"));
        assertThat(item.getAlarmRecoveryTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00"));

        assertThat(item.getMetric().getNamespace()).isEqualTo("SYS.VPC");
        assertThat(item.getMetric().getMetricName()).isEqualTo("downstream_bandwidth");
        assertThat(item.getMetric().getDimensions()).hasSize(1);

        assertThat(item.getCondition().getPeriod().getValue()).isEqualTo(1);
        assertThat(item.getCondition().getFilter()).isEqualTo("average");
        assertThat(item.getCondition().getComparisonOperator()).isEqualTo(">=");
        assertThat(item.getCondition().getValue()).isEqualTo(0.0);
        assertThat(item.getCondition().getCount()).isEqualTo(3);
        assertThat(item.getCondition().getSuppressDuration().getValue()).isEqualTo(3600);

        assertThat(item.getAlarmActions()).hasSize(1);
        assertThat(item.getOkActions()).hasSize(1);
        assertThat(item.getDataPoints()).hasSize(3);
        assertThat(item.getAdditionalInfo()).isNotNull();
    }

    @Test
    @DisplayName("Adapter maps sample SDK response to DTO with no field loss (deleting a DTO field would fail)")
    void adapterMapsLosslessly() throws IOException {
        ListAlarmHistoriesResponse sdkResp = loadSampleResponse();
        when(cesV2Client.listAlarmHistories(any(ListAlarmHistoriesRequest.class))).thenReturn(sdkResp);

        CesListAlarmsResponse out = adapter.listAlarms(new CesListAlarmsRequest(
                null, null, null, null, null, null, null, null, 0, 100));

        // ---- top-level response ----
        assertThat(out.total()).isEqualTo(103);
        assertThat(out.alarms()).hasSize(1);

        CesAlarmHistory item = out.alarms().get(0);

        // ---- legacy v1-style fields (backward compat) ----
        assertThat(item.alarmId()).isEqualTo("al1604473987569z6n6nkpm1");
        assertThat(item.alarmName()).isEqualTo("TC_CES_FunctionBaseline_Alarm_008");
        assertThat(item.alarmDescription()).isNull();  // v2 doesn't carry description
        assertThat(item.alarmLevel()).isEqualTo(2);
        assertThat(item.alarmType()).isEqualTo("ALL_INSTANCE");
        assertThat(item.alarmStatus()).isEqualTo("alarm");
        assertThat(item.namespace()).isEqualTo("SYS.VPC");
        assertThat(item.metricName()).isEqualTo("downstream_bandwidth");
        assertThat(item.triggerTime())
                .isEqualTo(OffsetDateTime.parse("2024-02-11T06:48:08+08:00").toInstant().toEpochMilli());
        assertThat(item.updateTime())
                .isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00").toInstant().toEpochMilli());

        // ---- v2 new fields ----
        assertThat(item.recordId()).isEqualTo("ah251222T092004SAD2yARym");
        assertThat(item.actionEnabled()).isFalse();
        assertThat(item.beginTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T05:48:08+08:00"));
        assertThat(item.endTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00"));
        assertThat(item.firstAlarmTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T06:48:08+08:00"));
        assertThat(item.lastAlarmTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00"));
        assertThat(item.alarmRecoveryTime()).isEqualTo(OffsetDateTime.parse("2024-02-11T08:48:08+08:00"));

        // ---- metric sub-record ----
        CesAlarmMetric metric = item.metric();
        assertThat(metric).isNotNull();
        assertThat(metric.namespace()).isEqualTo("SYS.VPC");
        assertThat(metric.metricName()).isEqualTo("downstream_bandwidth");
        assertThat(metric.dimensions()).hasSize(1);
        assertThat(metric.dimensions().get(0).name()).isEqualTo("bandwidth_id");
        assertThat(metric.dimensions().get(0).value()).isEqualTo("79a9cc0c-f626-4f15-bf99-a1f184107f88");

        // ---- condition sub-record ----
        CesAlarmCondition condition = item.condition();
        assertThat(condition).isNotNull();
        assertThat(condition.period()).isEqualTo(1);
        assertThat(condition.filter()).isEqualTo("average");
        assertThat(condition.comparisonOperator()).isEqualTo(">=");
        assertThat(condition.value()).isEqualTo(0.0);
        assertThat(condition.unit()).isEmpty();
        assertThat(condition.count()).isEqualTo(3);
        assertThat(condition.suppressDuration()).isEqualTo(3600);

        // ---- additional info sub-record ----
        CesAlarmAdditionalInfo info = item.additionalInfo();
        assertThat(info).isNotNull();
        assertThat(info.resourceId()).isEmpty();
        assertThat(info.resourceName()).isEmpty();
        assertThat(info.eventId()).isEmpty();

        // ---- action lists ----
        assertThat(item.alarmActions()).hasSize(1);
        CesAlarmAction alarmAction = item.alarmActions().get(0);
        assertThat(alarmAction.type()).isEqualTo("notification");
        assertThat(alarmAction.notificationList())
                .containsExactly("urn:smn:cn-north-4:abc123:topic-prod-alarm");

        assertThat(item.okActions()).hasSize(1);
        CesAlarmAction okAction = item.okActions().get(0);
        assertThat(okAction.type()).isEqualTo("notification");
        assertThat(okAction.notificationList())
                .containsExactly("urn:smn:cn-north-4:abc123:topic-prod-ok");

        // ---- data points ----
        assertThat(item.dataPoints()).hasSize(3);
        CesAlarmDataPoint point0 = item.dataPoints().get(0);
        assertThat(point0.time()).isEqualTo("2022-06-22T16:38:02+08:00");
        assertThat(point0.value()).isEqualTo(873.1507798960139);
        assertThat(item.dataPoints().get(1).value()).isEqualTo(883.1507798960139);
        assertThat(item.dataPoints().get(2).value()).isEqualTo(873.4);
    }

    private ListAlarmHistoriesResponse loadSampleResponse() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addModule(new JavaTimeModule())
                .build();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/ces/list-alarm-histories-v2-response.json")) {
            assertThat(in).as("sample JSON resource must exist").isNotNull();
            return mapper.readValue(in, ListAlarmHistoriesResponse.class);
        }
    }
}
