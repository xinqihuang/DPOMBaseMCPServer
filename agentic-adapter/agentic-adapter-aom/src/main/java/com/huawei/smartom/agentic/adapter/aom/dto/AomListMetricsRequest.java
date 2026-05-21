package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * Input DTO for listing AOM metrics.
 *
 * <p>Either {@code namespace} or {@code inventoryId} must be provided (validated by the service
 * layer). When both are present the adapter will use the {@code inventoryId} path.
 * {@code limit} defaults to 100 and {@code start} defaults to 0 when null.
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
