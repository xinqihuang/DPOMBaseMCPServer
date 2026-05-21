package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single AOM metric dimension (name/value pair).
 */
public record AomMetricDimension(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
}
