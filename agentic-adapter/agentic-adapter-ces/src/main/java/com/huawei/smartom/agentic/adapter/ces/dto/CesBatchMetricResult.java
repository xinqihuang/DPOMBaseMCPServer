/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code batch_query_ces_metric_data} 工具响应中单条指标的查询结果，
 * 对应华为云 CES {@code BatchListMetricData} 响应数组里的一个元素。
 *
 * <p>{@code unit} 为该指标的统一单位，{@link CesDatapoint#unit()} 将保持为 {@code null}
 * 以避免冗余。
 *
 * @param namespace  指标命名空间
 * @param metricName 指标名
 * @param dimensions 维度列表，可能为空但不会为 {@code null}
 * @param unit       指标单位（如 {@code %}），可能为 {@code null}
 * @param datapoints 数据点列表，按时间升序，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-06-02
 */
public record CesBatchMetricResult(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions,
        String unit,
        List<CesDatapoint> datapoints) {
}
