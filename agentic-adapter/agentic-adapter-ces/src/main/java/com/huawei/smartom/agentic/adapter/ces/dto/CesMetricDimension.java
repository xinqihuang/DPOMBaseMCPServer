package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One dimension of a CES metric.
 *
 * @param name  dimension name (e.g. {@code instance_id})
 * @param value dimension value (e.g. resource id)
 */
public record CesMetricDimension(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
}
