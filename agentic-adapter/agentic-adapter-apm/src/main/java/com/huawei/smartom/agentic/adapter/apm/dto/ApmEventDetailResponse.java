/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code show_event_detail} 工具的响应 DTO，对应 SDK {@code ShowEventDetailResponse}
 * 的无损投影（§4.1）。
 *
 * @param eventInfo 事件详情（详情形态下 tags/attachment 内容更全），上游缺省时可为 {@code null}
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmEventDetailResponse(
        @JsonProperty("event_info") ApmSpanEvent eventInfo) {
}
