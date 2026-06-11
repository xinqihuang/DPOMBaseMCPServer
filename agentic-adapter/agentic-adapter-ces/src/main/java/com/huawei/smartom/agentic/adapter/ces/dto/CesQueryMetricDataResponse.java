/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code query_ces_metric_data} 工具的响应 DTO，封装单条指标在指定时间区间内的数据点列表。
 *
 * @param metricName        指标名（来自上游响应，可能为 {@code null}）
 * @param datapoints        数据点列表，按时间升序，可能为空但不会为 {@code null}
 * @param resolvedNamespace 实际取数的命名空间（T24）。通常等于请求入参；
 *                          仅 {@code SYS.RDS} 经 service 层 fallback 路由到
 *                          {@code SYS.RDS_MYSQL_CLUSTER} 时与入参不同——禁止静默替换，
 *                          Agent 与诊断报告需要知道数据来源
 * @author h00884391
 * @since 2026-05-28
 */
public record CesQueryMetricDataResponse(
        String metricName,
        List<CesDatapoint> datapoints,
        String resolvedNamespace) {
}
