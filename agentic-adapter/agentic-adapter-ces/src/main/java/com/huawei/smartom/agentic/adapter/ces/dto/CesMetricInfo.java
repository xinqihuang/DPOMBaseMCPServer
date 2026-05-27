/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 单个 CES 指标定义的元数据。
 *
 * @param namespace   CES 命名空间（例如 {@code SYS.ECS}）
 * @param metricName  指标名称（例如 {@code cpu_util}）
 * @param unit        指标单位（例如 {@code %}）
 * @param dimensions  标识该指标所属资源的维度列表
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesMetricInfo(
        @JsonProperty("namespace") String namespace,
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("unit") String unit,
        @JsonProperty("dimensions") List<CesMetricDimension> dimensions) {
}
