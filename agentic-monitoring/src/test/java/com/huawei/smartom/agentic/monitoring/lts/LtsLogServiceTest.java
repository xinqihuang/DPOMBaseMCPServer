/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.lts;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapter;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
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
 * {@link LtsLogService} 单元测试。
 *
 * <p>覆盖 T17 任务卡 UT-S1 ~ UT-S8：必填项、时间窗、limit 范围、search_type 取值、
 * line_num / cursor_time 成对，以及合法用例委托给 adapter。
 *
 * @author h00884391
 * @since 2026-06-03
 */
class LtsLogServiceTest {

    private LtsLogAdapter adapter;
    private LtsLogService service;

    @BeforeEach
    void setUp() {
        adapter = mock(LtsLogAdapter.class);
        service = new LtsLogService(adapter);
    }

    @Test
    @DisplayName("UT-S1: null request -> INVALID_PARAM")
    void ut01NullRequest() {
        assertThatThrownBy(() -> service.queryLogs(null))
                .isInstanceOf(InvalidParamException.class)
                .matches(ex -> ((InvalidParamException) ex).getErrorCode() == ErrorCode.INVALID_PARAM);
        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S2: blank log_group_id -> INVALID_PARAM")
    void ut02BlankLogGroupId() {
        LtsListLogsRequest req = requestBuilder()
                .withLogGroupId("   ").build();
        assertThatThrownBy(() -> service.queryLogs(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("log_group_id");
        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S3: blank log_stream_id -> INVALID_PARAM")
    void ut03BlankLogStreamId() {
        LtsListLogsRequest req = requestBuilder()
                .withLogStreamId(null).build();
        assertThatThrownBy(() -> service.queryLogs(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("log_stream_id");
        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S4: start_time_millis >= end_time_millis -> INVALID_PARAM")
    void ut04InvalidTimeWindow() {
        LtsListLogsRequest req = requestBuilder()
                .withTimeWindow(1700003600000L, 1700000000000L).build();
        assertThatThrownBy(() -> service.queryLogs(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("strictly less");
        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S5: limit out of [1, 5000] -> INVALID_PARAM")
    void ut05LimitOutOfRange() {
        LtsListLogsRequest tooSmall = requestBuilder().withLimit(0).build();
        assertThatThrownBy(() -> service.queryLogs(tooSmall))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("limit");

        LtsListLogsRequest tooLarge = requestBuilder().withLimit(5001).build();
        assertThatThrownBy(() -> service.queryLogs(tooLarge))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("5000");

        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S6: unknown search_type -> INVALID_PARAM")
    void ut06UnknownSearchType() {
        LtsListLogsRequest req = requestBuilder()
                .withSearchType("random").build();
        assertThatThrownBy(() -> service.queryLogs(req))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("forwards/backwards");
        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S7: line_num without cursor_time (or vice versa) -> INVALID_PARAM")
    void ut07UnpairedCursor() {
        LtsListLogsRequest lineOnly = requestBuilder()
                .withCursor("100", null).build();
        assertThatThrownBy(() -> service.queryLogs(lineOnly))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("line_num and cursor_time");

        LtsListLogsRequest timeOnly = requestBuilder()
                .withCursor(null, "1700000000000").build();
        assertThatThrownBy(() -> service.queryLogs(timeOnly))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("line_num and cursor_time");

        verify(adapter, never()).listLogs(any());
    }

    @Test
    @DisplayName("UT-S8: full valid request -> delegated to adapter")
    void ut08DelegatesWhenValid() {
        LtsListLogsResponse expected =
                new LtsListLogsResponse(5, List.of(), Boolean.TRUE, List.of());
        when(adapter.listLogs(any(LtsListLogsRequest.class))).thenReturn(expected);

        LtsListLogsRequest req = requestBuilder()
                .withTimeWindow(1700000000000L, 1700003600000L)
                .withLimit(100)
                .withSearchType("forwards")
                .withCursor("100", "1700000000000")
                .build();

        LtsListLogsResponse out = service.queryLogs(req);

        assertThat(out).isSameAs(expected);
        verify(adapter).listLogs(req);
    }

    // ---- helpers ----

    private static RequestBuilder requestBuilder() {
        return new RequestBuilder();
    }

    /** Mutable builder for {@link LtsListLogsRequest} test inputs. */
    private static final class RequestBuilder {
        private String logGroupId = "lg-1";
        private String logStreamId = "ls-1";
        private Long startTimeMillis;
        private Long endTimeMillis;
        private Integer limit;
        private String searchType;
        private String lineNum;
        private String cursorTime;

        RequestBuilder withLogGroupId(String value) {
            this.logGroupId = value;
            return this;
        }

        RequestBuilder withLogStreamId(String value) {
            this.logStreamId = value;
            return this;
        }

        RequestBuilder withTimeWindow(Long start, Long end) {
            this.startTimeMillis = start;
            this.endTimeMillis = end;
            return this;
        }

        RequestBuilder withLimit(Integer value) {
            this.limit = value;
            return this;
        }

        RequestBuilder withSearchType(String value) {
            this.searchType = value;
            return this;
        }

        RequestBuilder withCursor(String line, String time) {
            this.lineNum = line;
            this.cursorTime = time;
            return this;
        }

        LtsListLogsRequest build() {
            return new LtsListLogsRequest(
                    logGroupId, logStreamId, startTimeMillis, endTimeMillis,
                    null, null, null, null, null, limit,
                    null, null, null, searchType,
                    lineNum, cursorTime, null);
        }
    }
}
