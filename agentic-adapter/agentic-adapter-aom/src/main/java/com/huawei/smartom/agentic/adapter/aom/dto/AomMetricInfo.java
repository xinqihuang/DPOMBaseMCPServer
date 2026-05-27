/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Metadata for a single AOM metric definition.
 *
 * <p>{@code dimensionValueHash} is an AOM-specific stable hash of the dimension value set,
 * useful for correlating the same metric dimension combination across calls.
 *
 * @param namespace          AOM namespace such as {@code PAAS.CONTAINER}
 * @param metricName         metric name (e.g. {@code cpuUsage})
 * @param unit               human-readable unit string, may be {@code null}
 * @param dimensions         dimension list identifying the resource this metric applies to
 * @param dimensionValueHash AOM-specific stable hash of the dimension value set
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomMetricInfo(
        @JsonProperty("namespace") String namespace,
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("unit") String unit,
        @JsonProperty("dimensions") List<AomMetricDimension> dimensions,
        @JsonProperty("dimension_value_hash") String dimensionValueHash) {
}
