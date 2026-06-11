/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmTraceAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

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
 * {@link ApmTraceService} 中 T29 根因诊断链三方法的单元测试（必填校验 + 透传）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class ApmTraceDiagnosisServiceTest {

    private ApmTraceAdapter adapter;
    private ApmTraceService service;

    @BeforeEach
    void setUp() {
        adapter = mock(ApmTraceAdapter.class);
        service = new ApmTraceService(adapter);
    }

    @Test
    @DisplayName("showTraceEvents: valid trace_id delegated; blank/null rejected")
    void traceEventsValidation() {
        ApmTraceEventsResponse expected = new ApmTraceEventsResponse(List.of());
        when(adapter.showTraceEvents("trace-abc")).thenReturn(expected);

        assertThat(service.showTraceEvents("trace-abc")).isSameAs(expected);

        assertThatThrownBy(() -> service.showTraceEvents(" "))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("trace_id");
        assertThatThrownBy(() -> service.showTraceEvents(null))
                .isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("showEventDetail: all four params required, valid request delegated")
    void eventDetailValidation() {
        ApmEventDetailResponse expected = new ApmEventDetailResponse(null);
        ApmEventDetailRequest valid =
                new ApmEventDetailRequest("trace-abc", "span-1", "evt-100", 1306682L);
        when(adapter.showEventDetail(valid)).thenReturn(expected);

        assertThat(service.showEventDetail(valid)).isSameAs(expected);

        assertThatThrownBy(() -> service.showEventDetail(null))
                .isInstanceOf(InvalidParamException.class);
        assertThatThrownBy(() -> service.showEventDetail(
                new ApmEventDetailRequest(" ", "span-1", "evt-100", 1L)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("trace_id");
        assertThatThrownBy(() -> service.showEventDetail(
                new ApmEventDetailRequest("t", null, "evt-100", 1L)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("span_id");
        assertThatThrownBy(() -> service.showEventDetail(
                new ApmEventDetailRequest("t", "s", "", 1L)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("event_id");
        assertThatThrownBy(() -> service.showEventDetail(
                new ApmEventDetailRequest("t", "s", "e", null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("env_id");
    }

    @Test
    @DisplayName("showClobDetail: env_id/clob_id required, business_id optional")
    void clobDetailValidation() {
        ApmClobDetailResponse expected = new ApmClobDetailResponse("stack...");
        ApmClobDetailRequest valid = new ApmClobDetailRequest(null, 1306682L, "clob-778");
        when(adapter.showClobDetail(valid)).thenReturn(expected);

        assertThat(service.showClobDetail(valid)).isSameAs(expected);

        assertThatThrownBy(() -> service.showClobDetail(null))
                .isInstanceOf(InvalidParamException.class);
        assertThatThrownBy(() -> service.showClobDetail(
                new ApmClobDetailRequest(7000L, null, "clob-778")))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("env_id");
        assertThatThrownBy(() -> service.showClobDetail(
                new ApmClobDetailRequest(7000L, 1306682L, " ")))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("clob_id");
    }

    @Test
    @DisplayName("invalid inputs never reach the adapter")
    void invalidInputsNeverReachAdapter() {
        assertThatThrownBy(() -> service.showTraceEvents(null))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).showTraceEvents(anyString());
        verify(adapter, never()).showEventDetail(any());
        verify(adapter, never()).showClobDetail(any());
    }
}
