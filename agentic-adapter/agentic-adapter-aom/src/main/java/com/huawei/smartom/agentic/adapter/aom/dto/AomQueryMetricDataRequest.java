/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * {@code query_aom_metric_data} 工具的请求 DTO，对应华为云 AOM {@code ListSample} 接口的入参。
 *
 * <p>{@code statistics} / {@code period} / {@code fillValue} 为受控枚举（T26，封闭集走
 * ADR-004 严格档）；{@code namespace} 因允许自定义命名空间保持 String（宽容目录档）。
 *
 * @param namespace  AOM 命名空间，例如 {@code PAAS.CONTAINER}
 * @param metricName 时间序列名，例如 {@code cpuUsage}
 * @param dimensions 维度过滤列表，可为空
 * @param statistics 统计方式列表，可选
 * @param period     采样粒度
 * @param timeRange  时间区间字符串，形如 {@code -1.-1.60}（最近 60 分钟）
 * @param fillValue  断点插值策略，可选
 * @author h00884391
 * @since 2026-05-28
 */
public record AomQueryMetricDataRequest(
        String namespace,
        String metricName,
        List<AomMetricDimension> dimensions,
        List<AomMetricStatistic> statistics,
        AomMetricPeriod period,
        String timeRange,
        AomFillValue fillValue) {
}
