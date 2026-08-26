/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmAlarmAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyResponse;
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
 * {@link ApmAlarmService} 单元测试。覆盖 T20 任务卡 UT-S1 ~ UT-S5。
 *
 * @author h00884391
 * @since 2026-06-10
 */
class ApmAlarmServiceTest {

    private ApmAlarmAdapter adapter;
    private ApmAlarmService service;

    @BeforeEach
    void setUp() {
        adapter = mock(ApmAlarmAdapter.class);
        service = new ApmAlarmService(adapter, 99999L);
    }

    @Test
    @DisplayName("UT-S1: null request -> INVALID_PARAM, adapter not called")
    void ut01NullRequest() {
        assertThatThrownBy(() -> service.listAlarmData(null))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).listAlarmData(any());
    }

    @Test
    @DisplayName("UT-S2: page < 1 -> INVALID_PARAM")
    void ut02PageTooSmall() {
        ApmAlarmDataRequest req = new ApmAlarmDataRequest(
                null, 0, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.listAlarmData(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("page");
        verify(adapter, never()).listAlarmData(any());
    }

    @Test
    @DisplayName("UT-S3: pageSize > 100 -> INVALID_PARAM")
    void ut03PageSizeTooBig() {
        ApmAlarmDataRequest req = new ApmAlarmDataRequest(
                null, null, 101, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.listAlarmData(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("page_size");
        verify(adapter, never()).listAlarmData(any());
    }

    @Test
    @DisplayName("UT-S4: businessId null AND no config default -> INVALID_PARAM")
    void ut04NoBusinessId() {
        service = new ApmAlarmService(adapter, null);
        ApmAlarmDataRequest req = new ApmAlarmDataRequest(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.listAlarmData(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("business_id");
        verify(adapter, never()).listAlarmData(any());
    }

    @Test
    @DisplayName("UT-S5: all valid -> delegate to adapter and return its response")
    void ut05ValidDelegates() {
        ApmAlarmDataResponse expected = new ApmAlarmDataResponse(List.of(), 0);
        when(adapter.listAlarmData(any(ApmAlarmDataRequest.class))).thenReturn(expected);

        ApmAlarmDataRequest req = new ApmAlarmDataRequest(
                7000L, 1, 50, "cn-north-4", null, null, null,
                null, null, null, null, null, null, null, null);

        ApmAlarmDataResponse out = service.listAlarmData(req);
        assertThat(out).isSameAs(expected);
        verify(adapter).listAlarmData(req);
    }

    // --- T21 listAlarmNotify validation cases ---

    @Test
    @DisplayName("UT-N1: null request -> INVALID_PARAM, adapter not called")
    void utN01NullRequest() {
        assertThatThrownBy(() -> service.listAlarmNotify(null))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).listAlarmNotify(any());
    }

    @Test
    @DisplayName("UT-N2: null alarm_data_id -> INVALID_PARAM")
    void utN02NullAlarmDataId() {
        ApmAlarmNotifyRequest req = new ApmAlarmNotifyRequest(null, null, 1, 50, null);
        assertThatThrownBy(() -> service.listAlarmNotify(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("alarm_data_id");
        verify(adapter, never()).listAlarmNotify(any());
    }

    @Test
    @DisplayName("UT-N3: non-positive alarm_data_id -> INVALID_PARAM")
    void utN03NonPositiveAlarmDataId() {
        ApmAlarmNotifyRequest req = new ApmAlarmNotifyRequest(null, 0, 1, 50, null);
        assertThatThrownBy(() -> service.listAlarmNotify(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("> 0");
        verify(adapter, never()).listAlarmNotify(any());
    }

    @Test
    @DisplayName("UT-N4: missing business_id (no config default) -> INVALID_PARAM")
    void utN04NoBusinessId() {
        service = new ApmAlarmService(adapter, null);
        ApmAlarmNotifyRequest req = new ApmAlarmNotifyRequest(null, 12345, 1, 50, null);
        assertThatThrownBy(() -> service.listAlarmNotify(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("business_id");
        verify(adapter, never()).listAlarmNotify(any());
    }

    @Test
    @DisplayName("UT-N5: pageSize out of range -> INVALID_PARAM")
    void utN05PageSizeOutOfRange() {
        ApmAlarmNotifyRequest req = new ApmAlarmNotifyRequest(null, 12345, 1, 101, null);
        assertThatThrownBy(() -> service.listAlarmNotify(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("page_size");
        verify(adapter, never()).listAlarmNotify(any());
    }

    @Test
    @DisplayName("UT-N6: all valid -> delegate to adapter")
    void utN06ValidDelegates() {
        ApmAlarmNotifyResponse expected = new ApmAlarmNotifyResponse(List.of(), 0);
        when(adapter.listAlarmNotify(any(ApmAlarmNotifyRequest.class))).thenReturn(expected);

        ApmAlarmNotifyRequest req = new ApmAlarmNotifyRequest(7000L, 12345, 1, 50, "cn-north-4");
        ApmAlarmNotifyResponse out = service.listAlarmNotify(req);
        assertThat(out).isSameAs(expected);
        verify(adapter).listAlarmNotify(req);
    }
}
