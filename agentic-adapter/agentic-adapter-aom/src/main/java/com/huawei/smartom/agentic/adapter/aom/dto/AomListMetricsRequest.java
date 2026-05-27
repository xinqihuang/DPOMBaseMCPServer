/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * 列出 AOM 指标的入参 DTO。
 *
 * <p>{@code namespace} 与 {@code inventoryId} 必须二选一（由服务层校验）。
 * 两者同时存在时，adapter 将走 {@code inventoryId} 路径。
 * 当为 null 时，{@code limit} 默认取 100，{@code start} 默认取 0。
 *
 * @param namespace   AOM 命名空间；当未设置 {@code inventoryId} 时必填
 * @param metricName  精确匹配的指标名称过滤条件，可选
 * @param dimensions  以 AND 方式组合的维度过滤列表，可选
 * @param inventoryId 资源清单 id（{@code resType_resId}）；当未设置 {@code namespace} 时必填
 * @param limit       页大小，取值范围 [1, 1000]；为 {@code null} 时默认 100
 * @param start       页偏移（0 起始）；为 {@code null} 时默认 0
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomListMetricsRequest(
        String namespace,
        String metricName,
        List<AomMetricDimension> dimensions,
        String inventoryId,
        Integer limit,
        Integer start) {

    /** 紧凑构造器：当分页参数缺省时填充默认值。 */
    public AomListMetricsRequest {
        if (limit == null) {
            limit = 100;
        }
        if (start == null) {
            start = 0;
        }
    }
}
