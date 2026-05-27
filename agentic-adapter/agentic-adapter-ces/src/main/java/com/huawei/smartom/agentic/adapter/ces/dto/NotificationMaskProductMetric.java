/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 按云产品维度屏蔽时的指标信息。
 *
 * @param dimensionName 维度名，多个用 {@code ","} 连接，长度 [0, 128]
 * @param metricName    指标名，长度 [1, 96]
 * @author h00884391
 * @since 2026-05-28
 */
public record NotificationMaskProductMetric(
        String dimensionName,
        String metricName) {
}
