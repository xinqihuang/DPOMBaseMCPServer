/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPagination;
import com.huawei.smartom.agentic.common.error.ErrorCode;
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
 * Unit tests for {@link CesMetricsService}.
 *
 * <p>Covers UT-04..08 (parameter validation) from the spec.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class CesMetricsServiceTest {

    private CesMetricsAdapter adapter;
    private CesMetricsService service;

    @BeforeEach
    void setUp() {
        adapter = mock(CesMetricsAdapter.class);
        service = new CesMetricsService(adapter);
    }

    @Test
    @DisplayName("UT-04: dim_name without dim_value -> INVALID_PARAM, adapter not called")
    void ut04DimNameOnly() {
        CesListMetricsRequest req = new CesListMetricsRequest(
                null, null, "instance_id", null, null, null, null);
        assertThatThrownBy(() -> service.listMetrics(req))
                .isInstanceOf(InvalidParamException.class)
                .matches(ex -> ((InvalidParamException) ex).getErrorCode() == ErrorCode.INVALID_PARAM)
                .hasMessageContaining("dim_name and dim_value");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-04 mirror: dim_value without dim_name -> INVALID_PARAM")
    void ut04DimValueOnly() {
        CesListMetricsRequest req = new CesListMetricsRequest(
                null, null, null, "abc-123", null, null, null);
        assertThatThrownBy(() -> service.listMetrics(req))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-05: limit=1001 -> INVALID_PARAM")
    void ut05LimitTooLarge() {
        CesListMetricsRequest req = new CesListMetricsRequest(
                null, null, null, null, 1001, null, null);
        assertThatThrownBy(() -> service.listMetrics(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("limit");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-06: limit=0 -> INVALID_PARAM")
    void ut06LimitZero() {
        CesListMetricsRequest req = new CesListMetricsRequest(
                null, null, null, null, 0, null, null);
        assertThatThrownBy(() -> service.listMetrics(req))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-07: namespace 'syc.ecs' (lowercase start) -> INVALID_PARAM")
    void ut07NamespaceLowercaseStart() {
        CesListMetricsRequest req = new CesListMetricsRequest(
                "syc.ecs", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.listMetrics(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("namespace");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("UT-08: order='random' -> INVALID_PARAM")
    void ut08OrderInvalid() {
        CesListMetricsRequest req = new CesListMetricsRequest(
                null, null, null, null, null, null, "random");
        assertThatThrownBy(() -> service.listMetrics(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("order");
        verify(adapter, never()).listMetrics(any());
    }

    @Test
    @DisplayName("Valid request delegates to adapter")
    void validRequestDelegates() {
        CesListMetricsResponse stub =
                new CesListMetricsResponse(List.of(), new CesPagination(0, 0, null, false));
        when(adapter.listMetrics(any())).thenReturn(stub);

        CesListMetricsRequest req = new CesListMetricsRequest(
                "SYS.ECS", "cpu_util", "instance_id", "abc", 50, null, "asc");
        CesListMetricsResponse out = service.listMetrics(req);

        assertThat(out).isSameAs(stub);
        verify(adapter).listMetrics(req);
    }

    @Test
    @DisplayName("Null request -> INVALID_PARAM")
    void nullRequest() {
        assertThatThrownBy(() -> service.listMetrics(null))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).listMetrics(any());
    }
}
