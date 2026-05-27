/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result DTO for listing AOM metrics: a page of metric definitions plus pagination metadata.
 *
 * @param metrics    metric definitions returned in the current page
 * @param pagination pagination metadata for paging through the full result set
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomListMetricsResponse(
        @JsonProperty("metrics") List<AomMetricInfo> metrics,
        @JsonProperty("pagination") AomPagination pagination) {
}
