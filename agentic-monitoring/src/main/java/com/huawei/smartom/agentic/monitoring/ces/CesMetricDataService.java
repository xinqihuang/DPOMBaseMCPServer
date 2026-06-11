/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPatterns;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CES 指标数据查询的业务编排。
 *
 * <p>对 {@code query_ces_metric_data} 工具的入参进行规约校验：
 * <ul>
 *   <li>必填项：{@code namespace} / {@code metricName} / {@code dimensions} / {@code filter} /
 *       {@code period} / {@code from} / {@code to}</li>
 *   <li>{@code dimensions} 长度 [1, 4]，每个维度的 name/value 不能为空</li>
 *   <li>{@code from} 必须严格小于 {@code to}</li>
 * </ul>
 *
 * <p>{@code filter} 与 {@code period} 已通过枚举强类型，不再在此校验取值集合。
 *
 * <p>T24：{@code namespace=SYS.RDS} 时先经 {@link CesRdsNamespaceResolver} 探测实例部署形态，
 * 集群版实例透明路由到 {@code SYS.RDS_MYSQL_CLUSTER}；实际取数的命名空间通过响应的
 * {@code resolved_namespace} 字段回显，不做静默替换。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class CesMetricDataService {

    private static final int MAX_DIMENSIONS = 4;

    private final CesMetricsAdapter adapter;
    private final CesRdsNamespaceResolver rdsResolver;

    /**
     * 构造一个由指定 adapter 支撑的 {@code CesMetricDataService}。
     *
     * @param adapter     执行实际 SDK 调用的 CES adapter
     * @param rdsResolver SYS_RDS 形态 fallback 解析器
     */
    public CesMetricDataService(CesMetricsAdapter adapter, CesRdsNamespaceResolver rdsResolver) {
        this.adapter = adapter;
        this.rdsResolver = rdsResolver;
    }

    /**
     * 校验入参、解析 SYS_RDS 形态后委托 adapter 执行查询。
     *
     * @param request 查询请求，不能为 null
     * @return 包含数据点的响应 DTO，{@code resolved_namespace} 标明实际取数的命名空间
     * @throws InvalidParamException 入参不符合规约，或 SYS.RDS 实例在两个 RDS
     *         namespace 下均无指标定义时抛出
     */
    public CesQueryMetricDataResponse queryMetricData(CesQueryMetricDataRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.queryMetricData(resolveRdsNamespace(request));
    }

    private CesQueryMetricDataRequest resolveRdsNamespace(CesQueryMetricDataRequest request) {
        if (!CesRdsNamespaceResolver.appliesTo(request.namespace())) {
            return request;
        }
        String resolved = rdsResolver.resolve(request.dimensions().get(0));
        if (resolved.equals(request.namespace())) {
            return request;
        }
        return new CesQueryMetricDataRequest(
                resolved, request.metricName(), request.dimensions(),
                request.filter(), request.period(), request.from(), request.to());
    }

    private void validate(CesQueryMetricDataRequest request) {
        Validations.requireNonBlank(request.namespace(), "namespace");
        if (!CesPatterns.NAMESPACE.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid, expected like 'SYS.ECS'");
        }
        Validations.requireNonBlank(request.metricName(), "metric_name");
        Validations.requireNonNull(request.filter(), "filter");
        Validations.requireNonNull(request.period(), "period");
        if (request.from() == null || request.to() == null) {
            throw new InvalidParamException("from and to are required");
        }
        if (request.from() >= request.to()) {
            throw new InvalidParamException(
                    "from must be strictly less than to (millis): from=" + request.from()
                            + ", to=" + request.to());
        }
        List<CesMetricDimension> dims = request.dimensions();
        if (dims == null || dims.isEmpty()) {
            throw new InvalidParamException("dimensions is required (at least 1)");
        }
        if (dims.size() > MAX_DIMENSIONS) {
            throw new InvalidParamException(
                    "dimensions length must be in [1, 4], got: " + dims.size());
        }
        for (CesMetricDimension dim : dims) {
            if (dim == null || Validations.isBlank(dim.name()) || Validations.isBlank(dim.value())) {
                throw new InvalidParamException(
                        "each dimension must have non-blank name and value");
            }
        }
    }
}
