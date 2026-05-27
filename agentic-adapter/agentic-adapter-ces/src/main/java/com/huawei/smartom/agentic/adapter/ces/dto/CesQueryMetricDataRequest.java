/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code query_ces_metric_data} 工具的请求 DTO，对应华为云 CES {@code ShowMetricData} 接口的入参。
 *
 * <p>{@code namespace}、{@code metricName}、{@code dimensions}、{@code filter}、{@code period}、
 * {@code from}、{@code to} 均为必填项；具体校验在业务层完成。
 *
 * @param namespace  CES 命名空间，例如 {@code SYS.ECS}
 * @param metricName 指标名，例如 {@code cpu_util}
 * @param dimensions 维度列表，最多 4 个，每个维度形如 {@code name=instance_id, value=...}
 * @param filter     聚合方式：{@code average} / {@code max} / {@code min} / {@code sum} / {@code variance}
 * @param period     聚合粒度（秒）：1 / 60 / 300 / 1200 / 3600 / 14400 / 86400
 * @param from       起始时间，毫秒级 UNIX 时间戳
 * @param to         结束时间，毫秒级 UNIX 时间戳（必须大于 {@code from}）
 * @author h00884391
 * @since 2026-05-28
 */
public record CesQueryMetricDataRequest(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions,
        String filter,
        Integer period,
        Long from,
        Long to) {
}
