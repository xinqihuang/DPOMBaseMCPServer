/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomEventAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomAlertType;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEventMetadataRelation;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse;
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
 * {@link AomEventService} 单元测试，覆盖 spec
 * {@code docs/specs/tools/list_aom_events.md} §4 的全部校验路径。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomEventServiceTest {

    private AomEventAdapter adapter;
    private AomEventService service;

    @BeforeEach
    void setUp() {
        adapter = mock(AomEventAdapter.class);
        service = new AomEventService(adapter);
    }

    @Test
    @DisplayName("valid request is delegated to the adapter unchanged")
    void validRequestDelegated() {
        AomListEventsRequest request = new AomListEventsRequest(
                AomAlertType.ACTIVE_ALERT, "-1.-1.60", 60000L, "cpu",
                List.of("starts_at"), "desc",
                List.of(new AomEventMetadataRelation("event_severity", List.of("Critical"), "AND")),
                100, "0");
        AomListEventsResponse expected = new AomListEventsResponse(List.of(), null);
        when(adapter.listEvents(request)).thenReturn(expected);

        assertThat(service.listEvents(request)).isSameAs(expected);
        verify(adapter).listEvents(request);
    }

    @Test
    @DisplayName("null request / missing or malformed time_range -> INVALID_PARAM")
    void timeRangeValidated() {
        assertThatThrownBy(() -> service.listEvents(null))
                .isInstanceOf(InvalidParamException.class);
        assertThatThrownBy(() -> service.listEvents(request(null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("time_range");
        assertThatThrownBy(() -> service.listEvents(request("last-60-min")))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("time_range");
        verify(adapter, never()).listEvents(any());
    }

    @Test
    @DisplayName("non-positive step or limit -> INVALID_PARAM")
    void stepAndLimitValidated() {
        assertThatThrownBy(() -> service.listEvents(new AomListEventsRequest(
                null, "-1.-1.60", 0L, null, null, null, null, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("step");
        assertThatThrownBy(() -> service.listEvents(new AomListEventsRequest(
                null, "-1.-1.60", null, null, null, null, null, 0, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("limit");
        verify(adapter, never()).listEvents(any());
    }

    @Test
    @DisplayName("sort_order without sort_order_by, or bad sort_order -> INVALID_PARAM")
    void sortPairValidated() {
        assertThatThrownBy(() -> service.listEvents(new AomListEventsRequest(
                null, "-1.-1.60", null, null, null, "desc", null, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("sort_order_by");
        assertThatThrownBy(() -> service.listEvents(new AomListEventsRequest(
                null, "-1.-1.60", null, null, List.of("starts_at"), "newest", null, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("sort_order");
        verify(adapter, never()).listEvents(any());
    }

    @Test
    @DisplayName("metadata_relation with blank key or bad relation -> INVALID_PARAM")
    void metadataRelationValidated() {
        assertThatThrownBy(() -> service.listEvents(new AomListEventsRequest(
                null, "-1.-1.60", null, null, null, null,
                List.of(new AomEventMetadataRelation(" ", List.of("Critical"), "AND")),
                null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("key");
        assertThatThrownBy(() -> service.listEvents(new AomListEventsRequest(
                null, "-1.-1.60", null, null, null, null,
                List.of(new AomEventMetadataRelation("event_severity", List.of("Critical"), "XOR")),
                null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("relation");
        verify(adapter, never()).listEvents(any());
    }

    private static AomListEventsRequest request(String timeRange) {
        return new AomListEventsRequest(
                null, timeRange, null, null, null, null, null, null, null);
    }
}
