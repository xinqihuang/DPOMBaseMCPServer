/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * Input DTO for {@code list_ces_metrics}.
 *
 * <p>The compact constructor normalises default values for {@code limit} and {@code order}.
 * Business validation (range, format) happens in the service layer so that violations
 * surface as {@code INVALID_PARAM} rather than {@code IllegalArgumentException}.
 *
 * @param namespace   CES namespace (e.g. {@code SYS.ECS}); nullable
 * @param metricName  exact metric name; nullable
 * @param dimName     dimension name; must accompany {@code dimValue} (or both null)
 * @param dimValue    dimension value; must accompany {@code dimName} (or both null)
 * @param limit       page size [1,1000]; defaults to 100 when null
 * @param start       pagination marker from previous page; nullable
 * @param order       sort order, "asc" or "desc"; defaults to "desc" when null
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
