package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Output DTO for {@code list_ces_metrics}.
 *
 * @param metrics     metric definitions, never null (empty when no result)
 * @param pagination  pagination metadata, never null
 */
public record CesListMetricsResponse(
        @JsonProperty("metrics") List<CesMetricInfo> metrics,
        @JsonProperty("pagination") CesPagination pagination) {
}
