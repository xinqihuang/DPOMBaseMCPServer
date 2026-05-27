/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination metadata returned by an AOM metrics listing.
 *
 * <p>Note: {@code nextToken} is an {@link Integer} offset — unlike CES which uses a String marker.
 *
 * @param count     number of items in the current page
 * @param total     total number of items across all pages, when known
 * @param offset    zero-based offset of the current page within the full result set, may be {@code null}
 * @param nextToken next-page offset marker; {@code null} when no more pages
 * @param hasMore   {@code true} when a subsequent page exists
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomPagination(
        @JsonProperty("count") int count,
        @JsonProperty("total") int total,
        @JsonProperty("offset") Integer offset,
        @JsonProperty("next_token") Integer nextToken,
        @JsonProperty("has_more") boolean hasMore) {
}
