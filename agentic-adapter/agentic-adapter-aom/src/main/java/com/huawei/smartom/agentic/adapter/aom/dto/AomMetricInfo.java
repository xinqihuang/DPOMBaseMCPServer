/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 单条 AOM 指标定义的元数据。
 *
 * <p>{@code dimensionValueHash} 是 AOM 针对维度值集合生成的稳定哈希，便于在多次调用之间关联同一维度组合的指标。
 *
 * @param namespace          AOM 命名空间，例如 {@code PAAS.CONTAINER}
 * @param metricName         指标名称，例如 {@code cpuUsage}
 * @param unit               可读的单位字符串，可能为 {@code null}
 * @param dimensions         标识该指标所属资源的维度列表
 * @param dimensionValueHash AOM 针对维度值集合生成的稳定哈希
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
