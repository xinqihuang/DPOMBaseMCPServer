/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * {@code query_aom_metric_data} 工具的请求 DTO，对应华为云 AOM {@code ListSample} 接口的入参。
 *
 * @param namespace  AOM 命名空间，例如 {@code PAAS.CONTAINER}
 * @param metricName 时间序列名，例如 {@code cpuUsage}
 * @param dimensions 维度过滤列表，可为空
 * @param statistics 统计方式列表（{@code maximum/minimum/sum/average/sampleCount}），可选
 * @param period     采样粒度（秒）：60 / 300 / 900 / 3600
 * @param timeRange  时间区间字符串，形如 {@code -1.-1.60}（最近 60 分钟）
 * @param fillValue  插值策略：{@code -1 / 0 / null / average}，可选
 * @author h00884391
 * @since 2026-05-28
 */
public record AomQueryMetricDataRequest(
        String namespace,
        String metricName,
        List<AomMetricDimension> dimensions,
        List<String> statistics,
        Integer period,
        String timeRange,
        String fillValue) {
}
