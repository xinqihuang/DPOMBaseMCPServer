/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPagination;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AomMetricsService} covering validation rules UT-04 to UT-11
 * from the {@code list_aom_metrics} spec.
 *
 * <p>Uses a mock {@link AomMetricsAdapter} and verifies that the adapter is never invoked
 * when input validation fails.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class AomMetricsServiceTest {

    private AomMetricsAdapter adapter;
    private AomMetricsService service;

    @BeforeEach
    void setUp() {
        adapter = mock(AomMetricsAdapter.class);
        service = new AomMetricsService(adapter);
    }

    @Test
    @DisplayName("UT-04: both namespace + inventory_id — no error, adapter invoked once")
    void ut04BothProvidedAdapterInvoked() {
        when(adapter.listMetrics(any())).thenReturn(emptyResponse());

        service.listMetrics(new AomListMetricsRequest(
                "PAAS.CONTAINER", null, null, "host_abc-123", null, null));

        verify(adapter, times(1)).listMetrics(any());
    }

    @Test
    @DisplayName("UT-05: no namespace, no inventory_id — INVALID_PARAM, adapter not called")
    void ut05NoBothThrowsInvalidParam() {
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest(null, null, null, null, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("namespace or inventory_id");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-06: dimension with empty value — INVALID_PARAM, adapter not called")
    void ut06EmptyDimensionValueThrows() {
        List<AomMetricDimension> dims = List.of(new AomMetricDimension("appName", ""));
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, dims, null, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("dimension");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-07: limit=1001 — INVALID_PARAM, adapter not called")
    void ut07LimitTooLargeThrows() {
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, 1001, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("limit");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-08: limit=0 — INVALID_PARAM, adapter not called")
    void ut08LimitZeroThrows() {
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, 0, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("limit");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-09: start=-1 — INVALID_PARAM, adapter not called")
    void ut09NegativeStartThrows() {
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest("PAAS.CONTAINER", null, null, null, null, -1)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("start");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-10: namespace='syc.ecs' (CES-style, invalid for AOM) — INVALID_PARAM, adapter not called")
    void ut10InvalidNamespaceFormatThrows() {
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest("syc.ecs", null, null, null, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("namespace");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-11: inventory_id='bogus_xxx' (unknown resType) — INVALID_PARAM, adapter not called")
    void ut11InvalidInventoryIdFormatThrows() {
        assertThatThrownBy(() -> service.listMetrics(
                new AomListMetricsRequest(null, null, null, "bogus_xxx", null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("inventory_id");
        verify(adapter, never()).listMetrics(any());
    }

    private AomListMetricsResponse emptyResponse() {
        return new AomListMetricsResponse(
                Collections.emptyList(),
                new AomPagination(0, 0, null, null, false));
    }
}
