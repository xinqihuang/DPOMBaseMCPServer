/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmTrendAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApmTrendService} 单元测试。覆盖 T22 任务卡 UT-S1 ~ UT-S6。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmTrendServiceTest {

    private ApmTrendAdapter adapter;
    private ApmTrendService service;

    @BeforeEach
    void setUp() {
        adapter = mock(ApmTrendAdapter.class);
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setApmBusinessId(99999L);
        service = new ApmTrendService(adapter, properties);
    }

    @Test
    @DisplayName("UT-S0: null request -> INVALID_PARAM, adapter not called")
    void ut00NullRequest() {
        assertThatThrownBy(() -> service.showTrend(null))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("request");
        verify(adapter, never()).showTrend(any());
    }

    @Test
    @DisplayName("UT-S1: viewConfig == null -> INVALID_PARAM")
    void ut01ViewConfigNull() {
        ApmTrendRequest req = new ApmTrendRequest(
                7000L, null, null, null, null, "t1", "t2");
        assertThatThrownBy(() -> service.showTrend(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("view_config");
        verify(adapter, never()).showTrend(any());
    }

    @Test
    @DisplayName("UT-S2: viewType not in whitelist -> INVALID_PARAM")
    void ut02ViewTypeIllegal() {
        ApmTrendViewConfig view = view("unknown", "latency");
        ApmTrendRequest req = new ApmTrendRequest(
                7000L, view, null, null, null, "t1", "t2");
        assertThatThrownBy(() -> service.showTrend(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("view_type");
        verify(adapter, never()).showTrend(any());
    }

    @Test
    @DisplayName("UT-S3: metricSet blank -> INVALID_PARAM")
    void ut03MetricSetBlank() {
        ApmTrendViewConfig view = view("trend", "  ");
        ApmTrendRequest req = new ApmTrendRequest(
                7000L, view, null, null, null, "t1", "t2");
        assertThatThrownBy(() -> service.showTrend(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("metric_set");
    }

    @Test
    @DisplayName("UT-S4: startTime / endTime blank -> INVALID_PARAM")
    void ut04TimeRangeBlank() {
        ApmTrendViewConfig view = view("trend", "latency");

        assertThatThrownBy(() -> service.showTrend(new ApmTrendRequest(
                7000L, view, null, null, null, "", "t2")))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("start_time");

        assertThatThrownBy(() -> service.showTrend(new ApmTrendRequest(
                7000L, view, null, null, null, "t1", null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("end_time");
    }

    @Test
    @DisplayName("UT-S5: businessId null + default null -> INVALID_PARAM")
    void ut05BusinessIdMissing() {
        HuaweiCloudProperties props = new HuaweiCloudProperties();
        ApmTrendService svc = new ApmTrendService(adapter, props);
        ApmTrendViewConfig view = view("trend", "latency");
        ApmTrendRequest req = new ApmTrendRequest(
                null, view, null, null, null, "t1", "t2");
        assertThatThrownBy(() -> svc.showTrend(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("business_id");
    }

    @Test
    @DisplayName("UT-S6: all valid -> delegates to adapter")
    void ut06ValidDelegates() {
        ApmTrendResponse expected = new ApmTrendResponse(List.of(), 0L);
        when(adapter.showTrend(any(ApmTrendRequest.class))).thenReturn(expected);

        ApmTrendViewConfig view = view("trend", "latency");
        ApmTrendRequest req = new ApmTrendRequest(
                7000L, view, null, 333L, null, "t1", "t2");
        ApmTrendResponse out = service.showTrend(req);
        assertThat(out).isSameAs(expected);
        verify(adapter).showTrend(req);
    }

    private ApmTrendViewConfig view(String viewType, String metricSet) {
        return new ApmTrendViewConfig(
                viewType, null, metricSet, null,
                null, null, null, null, null, null, null, null);
    }
}
