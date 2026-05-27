/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * Input DTO for listing AOM metrics.
 *
 * <p>Either {@code namespace} or {@code inventoryId} must be provided (validated by the service
 * layer). When both are present the adapter will use the {@code inventoryId} path.
 * {@code limit} defaults to 100 and {@code start} defaults to 0 when null.
 *
 * @param namespace   AOM namespace; required unless {@code inventoryId} is set
 * @param metricName  exact metric name filter, optional
 * @param dimensions  AND-combined dimension filter list, optional
 * @param inventoryId resource inventory id ({@code resType_resId}); required unless {@code namespace} is set
 * @param limit       page size in [1, 1000]; defaulted to 100 when {@code null}
 * @param start       page offset (0-based); defaulted to 0 when {@code null}
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

    /** Compact constructor: fill in default pagination values when absent. */
    public AomListMetricsRequest {
        if (limit == null) {
            limit = 100;
        }
        if (start == null) {
            start = 0;
        }
    }
}
