/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * 单条 AOM 时序的查询结果，包含序列定义与对应的数据点。
 *
 * @param namespace  命名空间
 * @param metricName 时间序列名
 * @param dimensions 维度列表，可能为空但不会为 {@code null}
 * @param datapoints 数据点列表，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record AomSampleSeries(
        String namespace,
        String metricName,
        List<AomMetricDimension> dimensions,
        List<AomMetricDatapoint> datapoints) {
}
