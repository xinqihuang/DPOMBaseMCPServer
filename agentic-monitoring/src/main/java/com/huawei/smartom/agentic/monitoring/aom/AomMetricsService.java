/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Business orchestration for AOM metric discovery.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate input per {@code list_aom_metrics} spec §3.2 (7 rules), throwing
 *       {@link InvalidParamException} on violation — before calling the adapter.</li>
 *   <li>Delegate to the adapter for the actual SDK call.</li>
 * </ul>
 *
 * <p>Validation uses short-circuit evaluation: rules are checked in order and the first violation
 * throws immediately, avoiding potential NPEs in later rules.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Service
public class AomMetricsService {

    /**
     * AOM namespace format: predefined PAAS.* values, CUSTOMMETRICS, or a custom namespace
     * matching {@code ^[A-Za-z][A-Za-z0-9_.]{2,63}$}.
     */
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
            "^(PAAS\\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$");

    /**
     * AOM inventory ID format: {@code resType_resId}, where resType is one of the AOM-defined
     * resource type tokens.
     */
    private static final Pattern INVENTORY_ID_PATTERN = Pattern.compile(
            "^(host|application|instance|container|process|network|storage|volume)_[A-Za-z0-9-]+$");

    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 1000;

    private final AomMetricsAdapter adapter;

    /**
     * Constructs a new {@code AomMetricsService} backed by the given adapter.
     *
     * @param adapter the AOM adapter that performs the actual SDK call
     */
    public AomMetricsService(AomMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Validate input then delegate to the adapter.
     *
     * @param request the query parameters; must not be null
     * @return the listing result
     * @throws InvalidParamException on input constraint violation
     */
    public AomListMetricsResponse listMetrics(AomListMetricsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listMetrics(request);
    }

    private void validate(AomListMetricsRequest request) {
        // Rule 1: namespace or inventory_id required
        if (isBlank(request.namespace()) && isBlank(request.inventoryId())) {
            throw new InvalidParamException(
                    "namespace or inventory_id must be provided (AOM API rejects empty body)");
        }

        // Rule 3: limit range [1, 1000]
        if (request.limit() < LIMIT_MIN || request.limit() > LIMIT_MAX) {
            throw new InvalidParamException(
                    "limit must be in [" + LIMIT_MIN + "," + LIMIT_MAX + "], got: " + request.limit());
        }

        // Rule 4: start >= 0
        if (request.start() < 0) {
            throw new InvalidParamException("start must be >= 0, got: " + request.start());
        }

        // Rule 5: dimensions — each element must have non-blank name and value
        List<AomMetricDimension> dims = request.dimensions();
        if (dims != null) {
            for (AomMetricDimension dim : dims) {
                if (isBlank(dim.name()) || isBlank(dim.value())) {
                    throw new InvalidParamException(
                            "each dimension must have non-blank name and value; "
                            + "got name='" + dim.name() + "', value='" + dim.value() + "'");
                }
            }
        }

        // Rule 6: namespace format (only validated when provided)
        if (!isBlank(request.namespace()) && !NAMESPACE_PATTERN.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid: '" + request.namespace()
                    + "'; expected PAAS.CONTAINER/PAAS.NODE/PAAS.SLA/PAAS.AGGR/CUSTOMMETRICS "
                    + "or a custom namespace matching [A-Za-z][A-Za-z0-9_.]{2,63}");
        }

        // Rule 7: inventory_id format (only validated when provided)
        if (!isBlank(request.inventoryId())
                && !INVENTORY_ID_PATTERN.matcher(request.inventoryId()).matches()) {
            throw new InvalidParamException(
                    "inventory_id format invalid: '" + request.inventoryId()
                    + "'; expected resType_resId where resType is one of: "
                    + "host/application/instance/container/process/network/storage/volume");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
