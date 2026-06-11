/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPatterns;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AOM 时序数据查询的业务编排。
 *
 * <p>对 {@code query_aom_metric_data} 工具入参进行规约校验。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class AomMetricDataService {

    private static final int MAX_DIMENSIONS = 20;

    private final AomMetricsAdapter adapter;

    /**
     * 构造一个 {@code AomMetricDataService}。
     *
     * @param adapter 执行实际 SDK 调用的 AOM adapter
     */
    public AomMetricDataService(AomMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行查询。
     *
     * @param request 查询请求，不能为 null
     * @return 时序数据响应
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public AomQueryMetricDataResponse queryMetricData(AomQueryMetricDataRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.queryMetricData(request);
    }

    private void validate(AomQueryMetricDataRequest request) {
        Validations.requireNonBlank(request.namespace(), "namespace");
        if (!AomPatterns.NAMESPACE.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid, expected PAAS.* / CUSTOMMETRICS or [A-Za-z]..., got: "
                            + request.namespace());
        }
        Validations.requireNonBlank(request.metricName(), "metric_name");
        // statistics / period / fill_value 已通过受控枚举强类型（T26），仅保留必填与元素非空校验
        Validations.requireNonNull(request.period(), "period");
        if (Validations.isBlank(request.timeRange())
                || !AomPatterns.TIME_RANGE.matcher(request.timeRange()).matches()) {
            throw new InvalidParamException(
                    "time_range must follow 'startMs.endMs.durationMin' (use -1 as placeholder), got: "
                            + request.timeRange());
        }
        if (request.statistics() != null && request.statistics().contains(null)) {
            throw new InvalidParamException("statistics entries must not be null");
        }
        List<AomMetricDimension> dims = request.dimensions();
        if (dims != null) {
            if (dims.size() > MAX_DIMENSIONS) {
                throw new InvalidParamException(
                        "dimensions length must be <= 20, got: " + dims.size());
            }
            for (AomMetricDimension dim : dims) {
                if (dim == null || Validations.isBlank(dim.name()) || Validations.isBlank(dim.value())) {
                    throw new InvalidParamException(
                            "each dimension must have non-blank name and value");
                }
            }
        }
    }
}
