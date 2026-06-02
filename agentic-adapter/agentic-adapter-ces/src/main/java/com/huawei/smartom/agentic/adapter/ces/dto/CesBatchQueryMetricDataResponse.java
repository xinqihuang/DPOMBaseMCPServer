/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code batch_query_ces_metric_data} 工具的响应 DTO，封装一次批量查询返回的所有指标结果。
 *
 * @param metrics 指标结果列表，与请求 {@code metrics} 的顺序对应，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-06-02
 */
public record CesBatchQueryMetricDataResponse(
        List<CesBatchMetricResult> metrics) {
}
