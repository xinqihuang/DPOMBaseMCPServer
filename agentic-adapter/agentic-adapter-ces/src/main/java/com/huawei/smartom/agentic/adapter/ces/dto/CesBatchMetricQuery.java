/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code batch_query_ces_metric_data} 工具中描述单个指标查询项的 DTO，对应华为云 CES
 * {@code BatchListMetricData} 接口请求体中 {@code metrics} 数组的一个元素。
 *
 * <p>{@code namespace}、{@code metricName} 必填；{@code dimensions} 长度 [1, 4]。具体校验在业务层完成。
 *
 * @param namespace  CES 命名空间，例如 {@code SYS.ECS}
 * @param metricName 指标名，例如 {@code cpu_util}
 * @param dimensions 维度列表，长度 [1, 4]
 * @author h00884391
 * @since 2026-06-02
 */
public record CesBatchMetricQuery(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions) {
}
