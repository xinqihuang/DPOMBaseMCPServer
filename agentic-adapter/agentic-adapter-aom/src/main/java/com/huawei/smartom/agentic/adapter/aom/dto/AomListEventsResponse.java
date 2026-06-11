/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code list_aom_events} 工具的响应 DTO，对应 SDK {@code ListEventsResponse} 的无损投影（§4.1）。
 *
 * @param events   事件/告警列表，可能为空但不会为 {@code null}
 * @param pageInfo 分页元数据，上游缺省时可为 {@code null}
 * @author h00884391
 * @since 2026-06-11
 */
public record AomListEventsResponse(
        @JsonProperty("events") List<AomEvent> events,
        @JsonProperty("page_info") AomEventPageInfo pageInfo) {
}
