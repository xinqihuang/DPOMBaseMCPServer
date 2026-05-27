/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;

/**
 * Port for querying AOM metric definitions.
 *
 * <p>Implementations wrap the Huawei Cloud AOM SDK and convert SDK types to our own DTOs.
 * SDK exceptions must be mapped to {@link com.huawei.smartom.agentic.common.exception.SmartomException}
 * before leaving the implementation.
 *
 * @author h00884391
 * @since 2026-05-21
 */
public interface AomMetricsAdapter {

    /**
     * List available AOM metric definitions matching the given request.
     *
     * @param request the query parameters; must not be null
     * @return a page of metric definitions with pagination metadata
     * @throws com.huawei.smartom.agentic.common.exception.SmartomException on SDK or upstream error
     */
    AomListMetricsResponse listMetrics(AomListMetricsRequest request);
}
