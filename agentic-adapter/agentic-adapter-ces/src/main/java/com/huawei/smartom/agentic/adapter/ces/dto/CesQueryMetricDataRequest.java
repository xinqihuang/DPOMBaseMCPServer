/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code query_ces_metric_data} 工具的请求 DTO，对应华为云 CES {@code ShowMetricData} 接口的入参。
 *
 * <p>{@code namespace}、{@code metricName}、{@code dimensions}、{@code filter}、{@code period}、
 * {@code from}、{@code to} 均为必填项；{@code filter}/{@code period} 使用枚举强类型，
 * {@code namespace}/{@code metricName} 仍以字符串承载以兼容未列入枚举的取值。
 *
 * @param namespace  CES 命名空间，例如 {@code SYS.ECS}
 * @param metricName 指标名，例如 {@code cpu_util}
 * @param dimensions 维度列表，最多 4 个，每个维度形如 {@code name=instance_id, value=...}
 * @param filter     聚合方式
 * @param period     聚合粒度
 * @param from       起始时间，毫秒级 UNIX 时间戳
 * @param to         结束时间，毫秒级 UNIX 时间戳（必须大于 {@code from}）
 * @author h00884391
 * @since 2026-05-28
 */
public record CesQueryMetricDataRequest(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions,
        CesMetricFilter filter,
        CesMetricPeriod period,
        Long from,
        Long to) {
}
