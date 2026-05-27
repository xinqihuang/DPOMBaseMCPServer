/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Business orchestration for CES metric queries.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate input per the {@code list_ces_metrics} spec, throwing
 *       {@link InvalidParamException} on violation</li>
 *   <li>Delegate to the adapter layer for the actual SDK call</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Service
public class CesMetricsService {

    /**
     * Per the CES API doc: namespace format is {@code service.item}, both starting with a letter,
     * total length [3,32] for service.
     */
    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("^[A-Z][A-Za-z0-9]{2,31}\\.[A-Za-z0-9_]+$");

    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 1000;

    private final CesMetricsAdapter adapter;

    /**
     * Constructs a new {@code CesMetricsService} backed by the given adapter.
     *
     * @param adapter the CES adapter that performs the actual SDK call
     */
    public CesMetricsService(CesMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Validate input then delegate to the adapter.
     *
     * @param request the request DTO, must not be null
     * @return the listing result
     * @throws InvalidParamException on input violation
     */
    public CesListMetricsResponse listMetrics(CesListMetricsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listMetrics(request);
    }

    private void validate(CesListMetricsRequest request) {
        // dim_name / dim_value must be provided together
        boolean dimNameMissing = request.dimName() == null;
        boolean dimValueMissing = request.dimValue() == null;
        if (dimNameMissing != dimValueMissing) {
            throw new InvalidParamException("dim_name and dim_value must be provided together");
        }

        // limit range
        if (request.limit() == null || request.limit() < LIMIT_MIN || request.limit() > LIMIT_MAX) {
            throw new InvalidParamException(
                    "limit must be in [" + LIMIT_MIN + "," + LIMIT_MAX + "]");
        }

        // namespace format
        if (request.namespace() != null && !NAMESPACE_PATTERN.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid, expected like 'SYS.ECS'");
        }

        // order enum
        String order = request.order();
        if (!"asc".equals(order) && !"desc".equals(order)) {
            throw new InvalidParamException("order must be 'asc' or 'desc'");
        }
    }
}
