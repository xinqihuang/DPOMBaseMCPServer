/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination metadata for CES list-style responses.
 *
 * @param count       items in the current page
 * @param total       total items available
 * @param nextMarker  opaque marker for the next page (null when exhausted)
 * @param hasMore     convenience flag, true iff there is at least one more page
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesPagination(
        @JsonProperty("count") int count,
        @JsonProperty("total") int total,
        @JsonProperty("next_marker") String nextMarker,
        @JsonProperty("has_more") boolean hasMore) {
}
