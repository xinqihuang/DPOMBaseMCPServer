package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Metadata for a single AOM metric definition.
 *
 * <p>{@code dimensionValueHash} is an AOM-specific stable hash of the dimension value set,
 * useful for correlating the same metric dimension combination across calls.
 */
public record AomMetricInfo(
        @JsonProperty("namespace") String namespace,
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("unit") String unit,
        @JsonProperty("dimensions") List<AomMetricDimension> dimensions,
        @JsonProperty("dimension_value_hash") String dimensionValueHash) {
}
