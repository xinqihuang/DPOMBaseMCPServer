package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Metadata for a single CES metric definition.
 *
 * @param namespace   CES namespace (e.g. {@code SYS.ECS})
 * @param metricName  metric name (e.g. {@code cpu_util})
 * @param unit        metric unit (e.g. {@code %})
 * @param dimensions  dimensions identifying the resource the metric applies to
 */
public record CesMetricInfo(
        @JsonProperty("namespace") String namespace,
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("unit") String unit,
        @JsonProperty("dimensions") List<CesMetricDimension> dimensions) {
}
