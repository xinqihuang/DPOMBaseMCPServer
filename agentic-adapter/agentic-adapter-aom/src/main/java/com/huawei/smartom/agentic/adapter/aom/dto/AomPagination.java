package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination metadata returned by an AOM metrics listing.
 *
 * <p>Note: {@code nextToken} is an {@link Integer} offset — unlike CES which uses a String marker.
 */
public record AomPagination(
        @JsonProperty("count") int count,
        @JsonProperty("total") int total,
        @JsonProperty("offset") Integer offset,
        @JsonProperty("next_token") Integer nextToken,
        @JsonProperty("has_more") boolean hasMore) {
}
