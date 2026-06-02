/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code batch_query_ces_metric_data} 工具的请求 DTO，对应华为云 CES {@code BatchListMetricData}
 * 接口的请求体。
 *
 * <p>{@code metrics} 必填，长度 [1, 500]；{@code filter}/{@code period}/{@code from}/{@code to}
 * 均为必填项。{@code filter}/{@code period} 使用枚举强类型，{@code metrics} 中的
 * {@code namespace}/{@code metricName} 仍以字符串承载。
 *
 * @param metrics 指标查询项列表，长度 [1, 500]
 * @param filter  聚合方式
 * @param period  聚合粒度
 * @param from    起始时间，毫秒级 UNIX 时间戳
 * @param to      结束时间，毫秒级 UNIX 时间戳（必须大于 {@code from}）
 * @author h00884391
 * @since 2026-06-02
 */
public record CesBatchQueryMetricDataRequest(
        List<CesBatchMetricQuery> metrics,
        CesMetricFilter filter,
        CesMetricPeriod period,
        Long from,
        Long to) {
}
