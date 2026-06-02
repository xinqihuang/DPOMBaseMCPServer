/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricQuery;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * CES 批量指标数据查询的业务编排。
 *
 * <p>对 {@code batch_query_ces_metric_data} 工具的入参进行规约校验：
 * <ul>
 *   <li>必填项：{@code metrics} / {@code filter} / {@code period} / {@code from} / {@code to}</li>
 *   <li>{@code metrics} 长度 [1, 500]，每个查询项的 {@code namespace}/{@code metricName} 不能为空，
 *       {@code dimensions} 长度 [1, 4]，每个维度的 name/value 不能为空</li>
 *   <li>{@code from} 必须严格小于 {@code to}</li>
 * </ul>
 *
 * <p>{@code filter} 与 {@code period} 已通过枚举强类型，不再在此校验取值集合。
 *
 * @author h00884391
 * @since 2026-06-02
 */
@Service
public class CesBatchMetricDataService {

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("^[A-Z][A-Za-z0-9]{2,31}\\.[A-Za-z0-9_]+$");

    private static final int MAX_METRICS = 500;
    private static final int MAX_DIMENSIONS = 4;

    private final CesMetricsAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code CesBatchMetricDataService}。
     *
     * @param adapter 执行实际 SDK 调用的 CES adapter
     */
    public CesBatchMetricDataService(CesMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行批量查询。
     *
     * @param request 查询请求，不能为 null
     * @return 包含每条指标查询结果的响应 DTO
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public CesBatchQueryMetricDataResponse batchQueryMetricData(CesBatchQueryMetricDataRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.batchQueryMetricData(request);
    }

    private void validate(CesBatchQueryMetricDataRequest request) {
        if (request.filter() == null) {
            throw new InvalidParamException("filter is required");
        }
        if (request.period() == null) {
            throw new InvalidParamException("period is required");
        }
        if (request.from() == null || request.to() == null) {
            throw new InvalidParamException("from and to are required");
        }
        if (request.from() >= request.to()) {
            throw new InvalidParamException(
                    "from must be strictly less than to (millis): from=" + request.from()
                            + ", to=" + request.to());
        }
        List<CesBatchMetricQuery> metrics = request.metrics();
        if (metrics == null || metrics.isEmpty()) {
            throw new InvalidParamException("metrics is required (at least 1)");
        }
        if (metrics.size() > MAX_METRICS) {
            throw new InvalidParamException(
                    "metrics length must be in [1, " + MAX_METRICS + "], got: " + metrics.size());
        }
        for (int idx = 0; idx < metrics.size(); idx++) {
            validateMetricQuery(metrics.get(idx), idx);
        }
    }

    private void validateMetricQuery(CesBatchMetricQuery query, int idx) {
        if (query == null) {
            throw new InvalidParamException("metrics[" + idx + "] must not be null");
        }
        if (isBlank(query.namespace())) {
            throw new InvalidParamException("metrics[" + idx + "].namespace is required");
        }
        if (!NAMESPACE_PATTERN.matcher(query.namespace()).matches()) {
            throw new InvalidParamException(
                    "metrics[" + idx + "].namespace format invalid, expected like 'SYS.ECS'");
        }
        if (isBlank(query.metricName())) {
            throw new InvalidParamException("metrics[" + idx + "].metric_name is required");
        }
        List<CesMetricDimension> dims = query.dimensions();
        if (dims == null || dims.isEmpty()) {
            throw new InvalidParamException(
                    "metrics[" + idx + "].dimensions is required (at least 1)");
        }
        if (dims.size() > MAX_DIMENSIONS) {
            throw new InvalidParamException(
                    "metrics[" + idx + "].dimensions length must be in [1, " + MAX_DIMENSIONS
                            + "], got: " + dims.size());
        }
        for (CesMetricDimension dim : dims) {
            if (dim == null || isBlank(dim.name()) || isBlank(dim.value())) {
                throw new InvalidParamException(
                        "metrics[" + idx + "] each dimension must have non-blank name and value");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
