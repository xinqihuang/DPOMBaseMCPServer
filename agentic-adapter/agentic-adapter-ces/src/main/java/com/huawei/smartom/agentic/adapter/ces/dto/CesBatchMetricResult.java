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
 * @param namespace         指标命名空间（上游响应回显）
 * @param metricName        指标名
 * @param dimensions        维度列表，可能为空但不会为 {@code null}
 * @param unit              指标单位（如 {@code %}），可能为 {@code null}
 * @param datapoints        数据点列表，按时间升序，可能为空但不会为 {@code null}
 * @param resolvedNamespace 实际取数的命名空间（T24）。通常等于请求入参；
 *                          仅 {@code SYS.RDS} 经 service 层 fallback 路由到
 *                          {@code SYS.RDS_MYSQL_CLUSTER} 时与入参不同——禁止静默替换，
 *                          Agent 与诊断报告需要知道数据来源
 * @author h00884391
 * @since 2026-06-02
 */
public record CesBatchMetricResult(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions,
        String unit,
        List<CesDatapoint> datapoints,
        String resolvedNamespace) {
}
