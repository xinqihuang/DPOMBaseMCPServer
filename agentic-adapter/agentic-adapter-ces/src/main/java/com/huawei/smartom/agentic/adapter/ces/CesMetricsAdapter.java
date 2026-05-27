/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * Adapter for Huawei Cloud CES metric definitions.
 *
 * <p>Implementations apply rate limiting, retries and exception mapping internally,
 * surfacing only {@link SmartomException} hierarchy on failure.
 *
 * @author h00884391
 * @since 2026-05-21
 */
public interface CesMetricsAdapter {

    /**
     * List CES metric definitions matching the given filter.
     *
     * @param request the filter / pagination request (must not be null)
     * @return matching metric definitions plus pagination metadata
     * @throws SmartomException with an appropriate {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesListMetricsResponse listMetrics(CesListMetricsRequest request);
}
