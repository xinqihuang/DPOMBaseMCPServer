/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 屏蔽规则关联的告警策略指标额外信息，对应 SDK v2 {@code MetricExtraInfo}。
 *
 * @param originMetricName 原始指标名（自定义指标场景下的真实指标）
 * @param metricPrefix     指标前缀
 * @param customProcName   自定义进程名
 * @param metricType       指标类型字面量
 * @author h00884391
 * @since 2026-06-10
 */
public record CesNotificationMaskMetricExtraInfo(
        String originMetricName,
        String metricPrefix,
        String customProcName,
        String metricType) {
}
