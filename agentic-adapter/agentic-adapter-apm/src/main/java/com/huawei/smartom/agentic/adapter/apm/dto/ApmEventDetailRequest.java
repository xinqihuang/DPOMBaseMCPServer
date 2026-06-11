/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code show_event_detail} 工具的请求 DTO，对应华为云 APM {@code ShowEventDetail}
 * 接口（GET /v1/apm2/openapi/view/trace/get-event-detail）的四个 query 参数。
 *
 * <p>四元组必须来自 {@code show_trace_events} 的真实响应（CLAUDE.md §4.3 (b)），全部必填。
 *
 * @param traceId trace id
 * @param spanId  span id
 * @param eventId 事件 id
 * @param envId   环境 id
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmEventDetailRequest(
        String traceId,
        String spanId,
        String eventId,
        Long envId) {
}
