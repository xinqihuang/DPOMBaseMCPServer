package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result DTO for listing AOM metrics: a page of metric definitions plus pagination metadata.
 */
public record AomListMetricsResponse(
        @JsonProperty("metrics") List<AomMetricInfo> metrics,
        @JsonProperty("pagination") AomPagination pagination) {
}
