/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single AOM metric dimension (name/value pair).
 *
 * @param name  dimension name (e.g. {@code appId})
 * @param value dimension value (e.g. the application id)
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomMetricDimension(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
}
