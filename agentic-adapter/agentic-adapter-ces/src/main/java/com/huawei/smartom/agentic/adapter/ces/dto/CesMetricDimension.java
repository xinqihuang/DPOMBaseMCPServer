/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One dimension of a CES metric.
 *
 * @param name  dimension name (e.g. {@code instance_id})
 * @param value dimension value (e.g. resource id)
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesMetricDimension(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
}
