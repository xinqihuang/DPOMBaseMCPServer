/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.core.exception.ClientRequestException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServerResponseException;
import com.huaweicloud.sdk.lts.v2.LtsClient;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextResponse;
import com.huaweicloud.sdk.lts.v2.model.ListLogsRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogsResponse;
import com.huaweicloud.sdk.lts.v2.model.LogContents;
import com.huaweicloud.sdk.lts.v2.model.QueryLtsLogParams;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LtsLogAdapterImpl} 的单元测试。
 *
 * <p>覆盖 T16 任务卡 §9 的 UT-01 ~ UT-09，验证 DTO ↔ SDK Request/Response 映射、
 * SearchType 容忍枚举、毫秒 Long → String 转换、上游异常映射。
 *
 * @author h00884391
 * @since 2026-06-03
 */
class LtsLogAdapterImplTest {

    private static final String RL = "lts-readonly";
    private static final String RETRY = "huaweicloud-retryable";

    private LtsClient ltsClient;
    private HuaweiCloudInvocation invocation;
    private LtsLogAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        ltsClient = mock(LtsClient.class);

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
                        throwable instanceof SmartomException smartomEx
                                && smartomEx.getErrorCode().isRetryable())
                .build());
        retryReg.retry(RETRY);

        invocation = new HuaweiCloudInvocation(rlReg, retryReg, new SdkExceptionMapper());
        adapter = new LtsLogAdapterImpl(ltsClient, invocation);
    }

    @Test
    @DisplayName("UT-01: listLogs maps full request body — millis to String, __time__, searchType, labels")
    void ut01ListLogsMapping() {
        when(ltsClient.listLogs(any(ListLogsRequest.class)))
                .thenReturn(new ListLogsResponse().withCount(0).withLogs(List.of()));

        LtsListLogsRequest req = new LtsListLogsRequest(
                "lg-1", "ls-1",
                1700000000000L, 1700003600000L,
                Map.of("hostname", "node-1"),
                "ERROR", "select * from t",
                Boolean.TRUE, Boolean.FALSE,
                500, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE,
                "forwards",
                "100", "1700000000000",
                "scroll-x");
        adapter.listLogs(req);

        ListLogsRequest sdkReq = captureListLogs();
        assertThat(sdkReq.getLogGroupId()).isEqualTo("lg-1");
        assertThat(sdkReq.getLogStreamId()).isEqualTo("ls-1");
        QueryLtsLogParams body = sdkReq.getBody();
        assertThat(body.getStartTime()).isEqualTo("1700000000000");
        assertThat(body.getEndTime()).isEqualTo("1700003600000");
        assertThat(body.getLabels()).containsEntry("hostname", "node-1");
        assertThat(body.getKeywords()).isEqualTo("ERROR");
        assertThat(body.getQuery()).isEqualTo("select * from t");
        assertThat(body.getIsAnalysisQuery()).isTrue();
        assertThat(body.getIsCount()).isFalse();
        assertThat(body.getLimit()).isEqualTo(500);
        assertThat(body.getIsDesc()).isTrue();
        assertThat(body.getHighlight()).isFalse();
        assertThat(body.getIsIterative()).isFalse();
        assertThat(body.getSearchType()).isEqualTo(QueryLtsLogParams.SearchTypeEnum.FORWARDS);
        assertThat(body.getLineNum()).isEqualTo("100");
        assertThat(body.getTime()).isEqualTo("1700000000000");
        assertThat(body.getScrollId()).isEqualTo("scroll-x");
    }

    @Test
    @DisplayName("UT-02: listLogs HTTP 429 -> retried 3x, then UPSTREAM_THROTTLED (retryable)")
    void ut02ListLogsThrottledRetried() {
        AtomicInteger calls = new AtomicInteger();
        when(ltsClient.listLogs(any(ListLogsRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(429, "LTS.0429", "throttled", "req-429");
                });

        assertThatThrownBy(() -> adapter.listLogs(minimalListLogsRequest()))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED)
                .matches(ex -> "req-429".equals(((SmartomException) ex).getUpstreamTraceId()));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-03: listLogs HTTP 401 -> no retry, UPSTREAM_AUTH_FAILED")
    void ut03ListLogsAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        when(ltsClient.listLogs(any(ListLogsRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(401, "APIGW.0301", "unauthorized", "req-401");
                });

        assertThatThrownBy(() -> adapter.listLogs(minimalListLogsRequest()))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_AUTH_FAILED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-04: listLogs HTTP 5xx -> retried 3x then UPSTREAM_ERROR")
    void ut04ListLogsServerError() {
        AtomicInteger calls = new AtomicInteger();
        when(ltsClient.listLogs(any(ListLogsRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ServerResponseException(503, "LTS.5030", "unavailable", "req-503");
                });

        assertThatThrownBy(() -> adapter.listLogs(minimalListLogsRequest()))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-05: listLogs SDK timeout -> TIMEOUT")
    void ut05ListLogsTimeout() {
        when(ltsClient.listLogs(any(ListLogsRequest.class)))
                .thenThrow(new RequestTimeoutException("read timed out"));

        assertThatThrownBy(() -> adapter.listLogs(minimalListLogsRequest()))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.TIMEOUT);
    }

    @Test
    @DisplayName("UT-06: listLogs non-empty analysisLogs is passed through")
    void ut06ListLogsAnalysisLogsPassthrough() {
        ListLogsResponse sdkResp = new ListLogsResponse()
                .withCount(2)
                .withLogs(List.of())
                .withIsQueryComplete(Boolean.TRUE)
                .withAnalysisLogs(List.of(Map.of("col", "val")));
        when(ltsClient.listLogs(any(ListLogsRequest.class))).thenReturn(sdkResp);

        LtsListLogsResponse out = adapter.listLogs(minimalListLogsRequest());

        assertThat(out.count()).isEqualTo(2);
        assertThat(out.logs()).isEmpty();
        assertThat(out.isQueryComplete()).isTrue();
        assertThat(out.analysisLogs()).hasSize(1);
    }

    @Test
    @DisplayName("UT-07: listLogContext maps body and preserves log order")
    void ut07ListLogContextMapping() {
        LogContents l1 = new LogContents().withContent("line-1").withLineNum("1");
        LogContents l2 = new LogContents().withContent("line-2").withLineNum("2");
        ListLogContextResponse sdkResp = new ListLogContextResponse()
                .withLogs(List.of(l1, l2))
                .withTotalCount(2)
                .withBackwardsCount(1)
                .withForwardsCount(1)
                .withIsQueryComplete(Boolean.TRUE);
        when(ltsClient.listLogContext(any(ListLogContextRequest.class))).thenReturn(sdkResp);

        LtsListLogContextResponse out = adapter.listLogContext(new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", 10, 20, null));

        ListLogContextRequest sdkReq = captureListLogContext();
        assertThat(sdkReq.getLogGroupId()).isEqualTo("lg-1");
        assertThat(sdkReq.getLogStreamId()).isEqualTo("ls-1");
        assertThat(sdkReq.getBody().getLineNum()).isEqualTo("100");
        assertThat(sdkReq.getBody().getTime()).isEqualTo("1700000000000");
        assertThat(sdkReq.getBody().getBackwardsSize()).isEqualTo(10);
        assertThat(sdkReq.getBody().getForwardsSize()).isEqualTo(20);
        assertThat(out.logs()).extracting("content").containsExactly("line-1", "line-2");
        assertThat(out.totalCount()).isEqualTo(2);
        assertThat(out.backwardsCount()).isEqualTo(1);
        assertThat(out.forwardsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-08: listLogContext HTTP 429 -> retried 3x then UPSTREAM_THROTTLED")
    void ut08ListLogContextThrottled() {
        AtomicInteger calls = new AtomicInteger();
        when(ltsClient.listLogContext(any(ListLogContextRequest.class)))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new ClientRequestException(429, "LTS.0429", "throttled", "req-429");
                });

        assertThatThrownBy(() -> adapter.listLogContext(new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", null, null, null)))
                .isInstanceOf(UpstreamException.class)
                .matches(ex -> ((SmartomException) ex).getErrorCode() == ErrorCode.UPSTREAM_THROTTLED);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("UT-09: listLogContext isQueryComplete=false is passed through")
    void ut09ListLogContextIncomplete() {
        ListLogContextResponse sdkResp = new ListLogContextResponse()
                .withLogs(List.of())
                .withTotalCount(0)
                .withIsQueryComplete(Boolean.FALSE);
        when(ltsClient.listLogContext(any(ListLogContextRequest.class))).thenReturn(sdkResp);

        LtsListLogContextResponse out = adapter.listLogContext(new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", null, null, null));

        assertThat(out.isQueryComplete()).isFalse();
        assertThat(out.logs()).isEmpty();
    }

    // ---- helpers ----

    private LtsListLogsRequest minimalListLogsRequest() {
        return new LtsListLogsRequest(
                "lg-1", "ls-1",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private ListLogsRequest captureListLogs() {
        ArgumentCaptor<ListLogsRequest> captor = ArgumentCaptor.forClass(ListLogsRequest.class);
        verify(ltsClient, times(1)).listLogs(captor.capture());
        return captor.getValue();
    }

    private ListLogContextRequest captureListLogContext() {
        ArgumentCaptor<ListLogContextRequest> captor =
                ArgumentCaptor.forClass(ListLogContextRequest.class);
        verify(ltsClient, times(1)).listLogContext(captor.capture());
        return captor.getValue();
    }
}
