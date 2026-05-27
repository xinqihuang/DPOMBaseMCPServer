/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * {@code list_ces_metrics} 的入参 DTO。
 *
 * <p>紧凑构造器负责为 {@code limit} 与 {@code order} 设置默认值。业务校验（取值范围、格式）放在服务层进行，
 * 以保证违例时返回 {@code INVALID_PARAM}，而非 {@code IllegalArgumentException}。
 *
 * @param namespace   CES 命名空间（例如 {@code SYS.ECS}），可为 null
 * @param metricName  精确指标名称，可为 null
 * @param dimName     维度名称，必须与 {@code dimValue} 同时提供，或同时为 null
 * @param dimValue    维度取值，必须与 {@code dimName} 同时提供，或同时为 null
 * @param limit       分页大小，取值 [1,1000]，为 null 时默认 100
 * @param start       上一页返回的分页标记，可为 null
 * @param order       排序方式，取值 "asc" 或 "desc"，为 null 时默认 "desc"
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesListMetricsRequest(
        String namespace,
        String metricName,
        String dimName,
        String dimValue,
        Integer limit,
        String start,
        String order) {

    public CesListMetricsRequest {
        if (limit == null) {
            limit = 100;
        }
        if (order == null) {
            order = "desc";
        }
    }
}
