/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSpanEvent;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DiagnoseTraceService} 单元测试。
 *
 * <p>用 mock 的 {@link ApmTraceService} 覆盖编排逻辑：可疑 span 挑选（出错 + 最慢）、
 * event 详情与 clob 下钻、阶段独立的非致命错误处理、入参校验。
 *
 * @author huangxinqi
 * @since 2026-06-18
 */
class DiagnoseTraceServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TRACE_ID = "trace-abc";

    private static final String ERROR_EVENT = "{\"span_id\":\"span-1\",\"event_id\":\"evt-100\","
            + "\"trace_id\":\"trace-abc\",\"env_id\":1306682,\"has_error\":true,\"time_used\":2456,"
            + "\"type\":\"SQL\",\"method\":\"querySql\",\"class_name\":\"com.example.OrderDao\","
            + "\"code\":500,\"error_reasons\":\"SQLTimeoutException\","
            + "\"tags\":{\"stacktrace_clob_id\":\"clob-777\"}}";

    private static final String SLOW_EVENT = "{\"span_id\":\"span-2\",\"event_id\":\"evt-101\","
            + "\"trace_id\":\"trace-abc\",\"env_id\":1306682,\"has_error\":false,\"time_used\":5000,"
            + "\"type\":\"HTTP\",\"method\":\"GET\"}";

    private ApmTraceService apmTraceService;
    private DiagnoseTraceService service;

    @BeforeEach
    void setUp() {
        apmTraceService = mock(ApmTraceService.class);
        service = new DiagnoseTraceService(apmTraceService);
    }

    @Test
    @DisplayName("挑出出错与最慢 span，并把详情里的 *_clob_id 解析为全文")
    void happyPathSelectsAndResolvesClob() {
        when(apmTraceService.getTopology(any()))
                .thenReturn(new ApmGetTopologyResponse("gtid-1", List.of(), List.of()));
        when(apmTraceService.showTraceEvents(TRACE_ID))
                .thenReturn(new ApmTraceEventsResponse(List.of(event(ERROR_EVENT), event(SLOW_EVENT))));
        when(apmTraceService.showEventDetail(any())).thenAnswer(inv -> {
            ApmEventDetailRequest r = inv.getArgument(0);
            return new ApmEventDetailResponse("span-1".equals(r.spanId())
                    ? detailWithClob("span-1", "clob-778") : event("{\"span_id\":\"" + r.spanId() + "\"}"));
        });
        when(apmTraceService.showClobDetail(any())).thenReturn(new ApmClobDetailResponse("RESOLVED"));

        DiagnoseTraceResponse resp = service.diagnose(new DiagnoseTraceRequest(TRACE_ID, 7000L, null, null));

        assertThat(resp.totalEventCount()).isEqualTo(2);
        assertThat(resp.errorEventCount()).isEqualTo(1);
        assertThat(resp.topology()).isNotNull();
        assertThat(resp.errors()).isEmpty();
        assertThat(resp.suspects()).hasSize(2);
        SuspectEvent s1 = bySpan(resp, "span-1");
        assertThat(s1.selectedReason()).isEqualTo("error");
        assertThat(s1.clobs()).hasSize(1);
        assertThat(s1.clobs().get(0).clobId()).isEqualTo("clob-778");
        assertThat(s1.clobs().get(0).text()).isEqualTo("RESOLVED");
        assertThat(bySpan(resp, "span-2").selectedReason()).isEqualTo("slow");
    }

    @Test
    @DisplayName("拓扑阶段失败仅记入 errors，可疑事件仍照常构建")
    void topologyFailureIsNonFatal() {
        when(apmTraceService.getTopology(any()))
                .thenThrow(new UpstreamException(ErrorCode.UPSTREAM_ERROR, "boom", "req-1", null));
        when(apmTraceService.showTraceEvents(anyString()))
                .thenReturn(new ApmTraceEventsResponse(List.of(event(ERROR_EVENT))));
        when(apmTraceService.showEventDetail(any()))
                .thenReturn(new ApmEventDetailResponse(detailWithClob("span-1", "clob-1")));
        when(apmTraceService.showClobDetail(any())).thenReturn(new ApmClobDetailResponse("T"));

        DiagnoseTraceResponse resp = service.diagnose(new DiagnoseTraceRequest(TRACE_ID, null, null, null));

        assertThat(resp.topology()).isNull();
        assertThat(resp.errors()).anyMatch(e -> "topology".equals(e.stage()));
        assertThat(resp.suspects()).hasSize(1);
    }

    @Test
    @DisplayName("clob 下钻失败时该 clob 文本为 null 并记入 errors")
    void clobFailureIsNonFatal() {
        when(apmTraceService.getTopology(any()))
                .thenReturn(new ApmGetTopologyResponse("g", List.of(), List.of()));
        when(apmTraceService.showTraceEvents(anyString()))
                .thenReturn(new ApmTraceEventsResponse(List.of(event(ERROR_EVENT))));
        when(apmTraceService.showEventDetail(any()))
                .thenReturn(new ApmEventDetailResponse(detailWithClob("span-1", "clob-9")));
        when(apmTraceService.showClobDetail(any()))
                .thenThrow(new UpstreamException(ErrorCode.UPSTREAM_THROTTLED, "429", "req-2", null));

        DiagnoseTraceResponse resp = service.diagnose(new DiagnoseTraceRequest(TRACE_ID, null, null, null));

        SuspectEvent s1 = bySpan(resp, "span-1");
        assertThat(s1.clobs().get(0).text()).isNull();
        assertThat(resp.errors()).anyMatch(e -> e.stage().startsWith("clob:"));
    }

    @Test
    @DisplayName("无出错事件时按 includeSlowest 默认纳入最慢 span")
    void noErrorEventsStillIncludesSlowest() {
        when(apmTraceService.getTopology(any()))
                .thenReturn(new ApmGetTopologyResponse("g", List.of(), List.of()));
        when(apmTraceService.showTraceEvents(anyString()))
                .thenReturn(new ApmTraceEventsResponse(List.of(event(SLOW_EVENT))));
        when(apmTraceService.showEventDetail(any()))
                .thenReturn(new ApmEventDetailResponse(event("{\"span_id\":\"span-2\"}")));

        DiagnoseTraceResponse resp = service.diagnose(new DiagnoseTraceRequest(TRACE_ID, null, null, null));

        assertThat(resp.errorEventCount()).isEqualTo(0);
        assertThat(resp.suspects()).hasSize(1);
        assertThat(resp.suspects().get(0).selectedReason()).isEqualTo("slow");
        verify(apmTraceService, never()).showClobDetail(any());
    }

    @Test
    @DisplayName("空白 trace_id 抛 INVALID_PARAM")
    void blankTraceIdRejected() {
        assertThatThrownBy(() -> service.diagnose(new DiagnoseTraceRequest("  ", null, null, null)))
                .isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("拒绝超过单次证据预算的 max_suspect_events")
    void excessiveSuspectBudgetRejected() {
        assertThatThrownBy(() -> service.diagnose(new DiagnoseTraceRequest(TRACE_ID, null, 21, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("[1, 20]");
        verify(apmTraceService, never()).showTraceEvents(anyString());
    }

    @Test
    @DisplayName("拒绝非正数 max_suspect_events")
    void nonPositiveSuspectBudgetRejected() {
        assertThatThrownBy(() -> service.diagnose(new DiagnoseTraceRequest(TRACE_ID, null, 0, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("[1, 20]");
    }

    private static ApmSpanEvent detailWithClob(String spanId, String clobId) {
        return event("{\"span_id\":\"" + spanId + "\",\"env_id\":1306682,"
                + "\"tags\":{\"sql_clob_id\":\"" + clobId + "\"}}");
    }

    private static SuspectEvent bySpan(DiagnoseTraceResponse resp, String spanId) {
        return resp.suspects().stream()
                .filter(s -> spanId.equals(s.spanId()))
                .findFirst()
                .orElseThrow();
    }

    private static ApmSpanEvent event(String json) {
        try {
            return MAPPER.readValue(json, ApmSpanEvent.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("bad test json: " + json, e);
        }
    }
}
