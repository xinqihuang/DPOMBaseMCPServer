/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * CES 指标查询的业务编排。
 *
 * <p>职责：
 * <ul>
 *   <li>按 {@code list_ces_metrics} 规约校验入参，若违反则抛出 {@link InvalidParamException}</li>
 *   <li>将实际的 SDK 调用委托给 adapter 层</li>
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
     * 构造一个由指定 adapter 支撑的 {@code CesMetricsService}。
     *
     * @param adapter 执行实际 SDK 调用的 CES adapter
     */
    public CesMetricsService(CesMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参，随后委托给 adapter 执行。
     *
     * @param request 请求 DTO，不能为 null
     * @return 列表查询结果
     * @throws InvalidParamException 入参违反约束时抛出
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
