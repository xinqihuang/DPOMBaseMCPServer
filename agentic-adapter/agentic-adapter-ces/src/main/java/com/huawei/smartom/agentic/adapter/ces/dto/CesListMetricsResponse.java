/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code list_ces_metrics} 的出参 DTO。
 *
 * @param metrics     指标定义列表，永不为 null（无结果时为空列表）
 * @param pagination  分页元数据，永不为 null
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesListMetricsResponse(
        @JsonProperty("metrics") List<CesMetricInfo> metrics,
        @JsonProperty("pagination") CesPagination pagination) {
}
