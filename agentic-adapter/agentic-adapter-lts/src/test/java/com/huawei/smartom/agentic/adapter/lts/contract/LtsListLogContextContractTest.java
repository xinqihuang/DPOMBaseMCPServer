/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.contract;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapterImpl;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsLogEntry;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.lts.v2.LtsClient;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextResponse;

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
 * LTS v2 {@code ListLogContext} 接口的 schema 契约测试。
 *
 * <p>T19 PR-5 防漂移：覆盖 {@link LtsListLogContextResponse} 全部 5 个字段（logs /
 * totalCount / backwardsCount / forwardsCount / isQueryComplete）+ {@link LtsLogEntry} 的
 * 3 字段。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class LtsListLogContextContractTest {

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
    @DisplayName("Adapter maps ListLogContext sample losslessly (5 top-level + 3 LogContents fields)")
    void adapterMapsLosslessly() throws IOException {
        ListLogContextResponse sdkResp = loadSample();
        when(ltsClient.listLogContext(any(ListLogContextRequest.class))).thenReturn(sdkResp);

        LtsListLogContextResponse out = adapter.listLogContext(new LtsListLogContextRequest(
                "lg-1", "ls-1", "1717400000000000001", "1718000000000",
                1, 1, null));

        assertThat(out.totalCount()).isEqualTo(3);
        assertThat(out.backwardsCount()).isEqualTo(1);
        assertThat(out.forwardsCount()).isEqualTo(1);
        assertThat(out.isQueryComplete()).isTrue();
        assertThat(out.logs()).hasSize(3);

        LtsLogEntry target = out.logs().get(1);
        assertThat(target.content()).contains("failed to commit");
        assertThat(target.lineNum()).isEqualTo("1717400000000000001");
        assertThat(target.labels()).containsEntry("severity", "ERROR");

        // bracketing entries
        assertThat(out.logs().get(0).content()).contains("received request");
        assertThat(out.logs().get(2).content()).contains("rolling back");
    }

    private ListLogContextResponse loadSample() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/lts/list-log-context-response.json")) {
            assertThat(in).as("sample JSON must exist").isNotNull();
            return mapper.readValue(in, ListLogContextResponse.class);
        }
    }
}
