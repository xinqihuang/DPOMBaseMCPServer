/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * CES 告警记录中的 metric 子对象，对应 SDK v2 {@code AlarmHistoryItemV2Metric}。
 *
 * <p>无损映射 SDK 三个字段；维度复用 {@link CesMetricDimension}。
 *
 * @param namespace  指标命名空间，例如 {@code SYS.ECS}
 * @param metricName 指标 id，例如 {@code cpu_util}
 * @param dimensions 维度列表，可能为 {@code null}
 * @author h00884391
 * @since 2026-06-10
 */
public record CesAlarmMetric(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions) {
}
