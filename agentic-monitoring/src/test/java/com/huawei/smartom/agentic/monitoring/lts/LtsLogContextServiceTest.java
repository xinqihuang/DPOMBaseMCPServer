/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.lts;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapter;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
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
 * {@link LtsLogContextService} 单元测试。
 *
 * <p>覆盖 T18 任务卡 UT-S1 ~ UT-S9：必填项、双模式互斥、size 范围、双 0 拒绝、合法委托。
 *
 * @author h00884391
 * @since 2026-06-03
 */
class LtsLogContextServiceTest {

    private LtsLogAdapter adapter;
    private LtsLogContextService service;

    @BeforeEach
    void setUp() {
        adapter = mock(LtsLogAdapter.class);
        service = new LtsLogContextService(adapter);
    }

    @Test
    @DisplayName("UT-S1: null request -> INVALID_PARAM")
    void ut01NullRequest() {
        assertThatThrownBy(() -> service.queryContext(null))
                .isInstanceOf(InvalidParamException.class)
                .matches(ex -> ((InvalidParamException) ex).getErrorCode() == ErrorCode.INVALID_PARAM);
        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S2: blank log_group_id -> INVALID_PARAM")
    void ut02BlankLogGroupId() {
        LtsListLogContextRequest req = new LtsListLogContextRequest(
                "  ", "ls-1", "100", "1700000000000", null, null, null);
        assertThatThrownBy(() -> service.queryContext(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("log_group_id");
        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S3: blank log_stream_id -> INVALID_PARAM")
    void ut03BlankLogStreamId() {
        LtsListLogContextRequest req = new LtsListLogContextRequest(
                "lg-1", null, "100", "1700000000000", null, null, null);
        assertThatThrownBy(() -> service.queryContext(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("log_stream_id");
        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S4: no cursor and no scroll_id -> INVALID_PARAM")
    void ut04NoCursorNoScroll() {
        LtsListLogContextRequest req = new LtsListLogContextRequest(
                "lg-1", "ls-1", null, null, null, null, null);
        assertThatThrownBy(() -> service.queryContext(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("either (line_num + cursor_time) or scroll_id");
        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S5: line_num without cursor_time and no scroll_id -> INVALID_PARAM")
    void ut05UnpairedCursorFirstMode() {
        LtsListLogContextRequest lineOnly = new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", null, null, null, null);
        assertThatThrownBy(() -> service.queryContext(lineOnly))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("either (line_num + cursor_time)");

        LtsListLogContextRequest timeOnly = new LtsListLogContextRequest(
                "lg-1", "ls-1", null, "1700000000000", null, null, null);
        assertThatThrownBy(() -> service.queryContext(timeOnly))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("either (line_num + cursor_time)");

        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S6: backwards_size out of [0, 500] -> INVALID_PARAM")
    void ut06SizeOutOfRange() {
        LtsListLogContextRequest negative = new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", -1, 10, null);
        assertThatThrownBy(() -> service.queryContext(negative))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("backwards_size");

        LtsListLogContextRequest tooLarge = new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", 10, 501, null);
        assertThatThrownBy(() -> service.queryContext(tooLarge))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("forwards_size");

        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S7: backwards_size=0 AND forwards_size=0 -> INVALID_PARAM")
    void ut07DoubleZeroSizes() {
        LtsListLogContextRequest req = new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", 0, 0, null);
        assertThatThrownBy(() -> service.queryContext(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("at least one of backwards_size / forwards_size");
        verify(adapter, never()).listLogContext(any());
    }

    @Test
    @DisplayName("UT-S8: scroll-only mode (line_num and cursor_time both null) -> delegated")
    void ut08ScrollOnlyMode() {
        LtsListLogContextResponse expected = new LtsListLogContextResponse(
                List.of(), 0, 0, 0, Boolean.TRUE);
        when(adapter.listLogContext(any(LtsListLogContextRequest.class))).thenReturn(expected);

        LtsListLogContextRequest req = new LtsListLogContextRequest(
                "lg-1", "ls-1", null, null, 10, 10, "scroll-x");
        LtsListLogContextResponse out = service.queryContext(req);

        assertThat(out).isSameAs(expected);
        verify(adapter).listLogContext(req);
    }

    @Test
    @DisplayName("UT-S9: full valid first-mode request -> delegated")
    void ut09FirstModeDelegated() {
        LtsListLogContextResponse expected = new LtsListLogContextResponse(
                List.of(), 0, 0, 0, Boolean.TRUE);
        when(adapter.listLogContext(any(LtsListLogContextRequest.class))).thenReturn(expected);

        LtsListLogContextRequest req = new LtsListLogContextRequest(
                "lg-1", "ls-1", "100", "1700000000000", 50, 100, null);
        LtsListLogContextResponse out = service.queryContext(req);

        assertThat(out).isSameAs(expected);
        verify(adapter).listLogContext(req);
    }
}
