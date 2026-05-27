/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

/**
 * 单个统计结果（统计方式 + 对应值）。
 *
 * @param statistic 统计方式，例如 {@code maximum}
 * @param value     统计结果数值，可能为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record AomStatisticValue(String statistic, Double value) {
}
