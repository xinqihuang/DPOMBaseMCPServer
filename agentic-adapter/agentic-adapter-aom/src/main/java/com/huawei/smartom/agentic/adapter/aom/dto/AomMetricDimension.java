/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个 AOM 指标维度，由名称/值对构成。
 *
 * @param name  维度名称，例如 {@code appId}
 * @param value 维度取值，例如对应的应用 id
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomMetricDimension(
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
}
