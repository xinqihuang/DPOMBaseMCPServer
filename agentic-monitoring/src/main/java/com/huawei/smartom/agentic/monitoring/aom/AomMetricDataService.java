/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
            "^(PAAS\\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$");

    private static final Pattern TIME_RANGE_PATTERN =
            Pattern.compile("^(-1|\\d{1,16})\\.(-1|\\d{1,16})\\.\\d{1,7}$");

    private static final Set<Integer> ALLOWED_PERIODS = Set.of(60, 300, 900, 3600);

    private static final Set<String> ALLOWED_STATISTICS =
            Set.of("maximum", "minimum", "sum", "average", "sampleCount");

    private static final Set<String> ALLOWED_FILL_VALUES =
            Set.of("-1", "0", "null", "average");

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
        if (isBlank(request.namespace())) {
            throw new InvalidParamException("namespace is required");
        }
        if (!NAMESPACE_PATTERN.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid, expected PAAS.* / CUSTOMMETRICS or [A-Za-z]..., got: "
                            + request.namespace());
        }
        if (isBlank(request.metricName())) {
            throw new InvalidParamException("metric_name is required");
        }
        if (request.period() == null || !ALLOWED_PERIODS.contains(request.period())) {
            throw new InvalidParamException(
                    "period must be 60/300/900/3600 (seconds), got: " + request.period());
        }
        if (isBlank(request.timeRange()) || !TIME_RANGE_PATTERN.matcher(request.timeRange()).matches()) {
            throw new InvalidParamException(
                    "time_range must follow 'startMs.endMs.durationMin' (use -1 as placeholder), got: "
                            + request.timeRange());
        }
        if (request.statistics() != null) {
            for (String stat : request.statistics()) {
                if (!ALLOWED_STATISTICS.contains(stat)) {
                    throw new InvalidParamException(
                            "statistics entry invalid: '" + stat
                                    + "'; allowed: maximum/minimum/sum/average/sampleCount");
                }
            }
        }
        if (request.fillValue() != null && !ALLOWED_FILL_VALUES.contains(request.fillValue())) {
            throw new InvalidParamException(
                    "fill_value must be one of -1/0/null/average, got: " + request.fillValue());
        }
        List<AomMetricDimension> dims = request.dimensions();
        if (dims != null) {
            if (dims.size() > MAX_DIMENSIONS) {
                throw new InvalidParamException(
                        "dimensions length must be <= 20, got: " + dims.size());
            }
            for (AomMetricDimension dim : dims) {
                if (dim == null || isBlank(dim.name()) || isBlank(dim.value())) {
                    throw new InvalidParamException(
                            "each dimension must have non-blank name and value");
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
