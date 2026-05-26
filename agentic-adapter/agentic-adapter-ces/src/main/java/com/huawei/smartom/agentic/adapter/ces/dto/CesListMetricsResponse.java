/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Output DTO for {@code list_ces_metrics}.
 *
 * @param metrics     metric definitions, never null (empty when no result)
 * @param pagination  pagination metadata, never null
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesListMetricsResponse(
        @JsonProperty("metrics") List<CesMetricInfo> metrics,
        @JsonProperty("pagination") CesPagination pagination) {
}
