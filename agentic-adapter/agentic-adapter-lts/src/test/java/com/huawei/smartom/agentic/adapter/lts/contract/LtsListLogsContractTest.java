/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.contract;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapterImpl;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsLogEntry;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.lts.v2.LtsClient;
import com.huaweicloud.sdk.lts.v2.model.ListLogsRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogsResponse;

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
 * LTS v2 {@code ListLogs} 接口的 schema 契约测试。
 *
 * <p>T19 PR-5 防漂移：覆盖 {@link LtsListLogsResponse} 全部 4 个字段（count / logs /
 * isQueryComplete / analysisLogs）以及 {@link LtsLogEntry} 的 3 个字段（content / lineNum /
 * labels）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class LtsListLogsContractTest {

    private LtsClient ltsClient;
    private LtsLogAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        ltsClient = mock(LtsClient.class);

        RateLimiterRegistry rlReg = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rlReg.rateLimiter("lts-readonly");

        RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(throwable ->
                        throwable instanceof SmartomException sx && sx.getErrorCode().isRetryable())
                .build());
        retryReg.retry("huaweicloud-retryable");

        adapter = new LtsLogAdapterImpl(ltsClient,
                new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper()));
    }

    @Test
    @DisplayName("Adapter maps ListLogs sample losslessly (4 top-level + 3 LogContents fields)")
    void adapterMapsLosslessly() throws IOException {
        ListLogsResponse sdkResp = loadSample();
        when(ltsClient.listLogs(any(ListLogsRequest.class))).thenReturn(sdkResp);

        LtsListLogsResponse out = adapter.listLogs(new LtsListLogsRequest(
                "lg-1", "ls-1",
                1718000000000L, 1718001000000L,
                null, "ERROR", null, null, null, 100,
                null, null, null, null, null, null, null));

        assertThat(out.count()).isEqualTo(2);
        assertThat(out.isQueryComplete()).isTrue();
        assertThat(out.analysisLogs()).isEmpty();
        assertThat(out.logs()).hasSize(2);

        LtsLogEntry entry0 = out.logs().get(0);
        assertThat(entry0.content()).contains("starting transaction id=tx-1");
        assertThat(entry0.lineNum()).isEqualTo("1717400000000000000");
        assertThat(entry0.labels()).containsEntry("host_name", "ecs-i-abc")
                .containsEntry("container_name", "order-svc");

        LtsLogEntry entry1 = out.logs().get(1);
        assertThat(entry1.content()).contains("failed to commit");
        assertThat(entry1.lineNum()).isEqualTo("1717400000000000001");
        assertThat(entry1.labels()).containsEntry("severity", "ERROR");
    }

    private ListLogsResponse loadSample() throws IOException {
        // ListLogs sample uses mixed snake_case + camelCase keys (line_num is snake but
        // isQueryComplete / analysisLogs are camel) so deserialise without a global naming
        // strategy and rely on per-field @JsonProperty.
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/lts/list-logs-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListLogsResponse.class);
        }
    }
}
