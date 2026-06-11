/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code show_trace_events} 工具的响应 DTO，对应 SDK {@code ShowTraceEventsResponse}
 * 的无损投影（§4.1）。
 *
 * @param spanEventList 该 trace 的全部调用链事件，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmTraceEventsResponse(
        @JsonProperty("span_event_list") List<ApmSpanEvent> spanEventList) {
}
