/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CES 指标的单个维度。
 *
 * @param name  维度名称（例如 {@code instance_id}）
 * @param value 维度取值（例如资源 ID）
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesMetricDimension(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
}
