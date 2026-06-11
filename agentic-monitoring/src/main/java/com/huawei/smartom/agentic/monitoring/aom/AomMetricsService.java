/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPatterns;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;
import com.huawei.smartom.agentic.monitoring.cache.DiscoveryCacheConfig;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * AOM 指标发现的业务编排。
 *
 * <p>职责：
 * <ul>
 *   <li>按 {@code list_aom_metrics} 规约 §3.2 的 7 条规则校验入参，若违反则抛出
 *       {@link InvalidParamException}，校验通过后再调用 adapter。</li>
 *   <li>将实际的 SDK 调用委托给 adapter。</li>
 *   <li>对查询结果做 Caffeine TTL 缓存（T26）：指标定义目录读频高、变化相对低；
 *       但 AOM 清单含容器/进程级对象，churn 快于 CES 云服务目录，TTL 默认 1h 而非 1d。</li>
 * </ul>
 *
 * <p>校验采用短路求值：规则按顺序逐条检查，首个违反立即抛出，避免在后续规则中触发空指针。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Service
public class AomMetricsService {

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
     * 构造一个由指定 adapter 支撑的 {@code AomMetricsService}。
     *
     * @param adapter 执行实际 SDK 调用的 AOM adapter
     */
    public AomMetricsService(AomMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参，随后委托给 adapter 执行。
     *
     * <p>结果按整个请求 record 作 key 缓存（record 值语义 equals 覆盖全部参数，
     * 紧凑构造器已归一化 limit/start 默认值）；失败（异常）与空结果不写缓存。
     *
     * @param request 查询参数，不能为 null
     * @return 列表查询结果
     * @throws InvalidParamException 入参约束违反时抛出
     */
    @Cacheable(
            cacheNames = DiscoveryCacheConfig.CACHE_AOM_METRICS,
            key = "#request",
            condition = "#request != null",
            unless = "#result == null or #result.metrics() == null or #result.metrics().isEmpty()")
    public AomListMetricsResponse listMetrics(AomListMetricsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listMetrics(request);
    }

    /**
     * 清空 {@code list_aom_metrics} 结果缓存。运维入口：当 AOM 侧新增指标定义
     * 而 TTL 尚未到期时，可经由本方法强制失效。
     */
    @CacheEvict(cacheNames = DiscoveryCacheConfig.CACHE_AOM_METRICS, allEntries = true)
    public void evictListMetricsCache() {
        // 注解驱动的整体失效，无方法体逻辑
    }

    private void validate(AomListMetricsRequest request) {
        // Rule 1: namespace or inventory_id required
        if (Validations.isBlank(request.namespace()) && Validations.isBlank(request.inventoryId())) {
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
                if (Validations.isBlank(dim.name()) || Validations.isBlank(dim.value())) {
                    throw new InvalidParamException(
                            "each dimension must have non-blank name and value; "
                            + "got name='" + dim.name() + "', value='" + dim.value() + "'");
                }
            }
        }

        // Rule 6: namespace format (only validated when provided)
        if (!Validations.isBlank(request.namespace())
                && !AomPatterns.NAMESPACE.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid: '" + request.namespace()
                    + "'; expected PAAS.CONTAINER/PAAS.NODE/PAAS.SLA/PAAS.AGGR/CUSTOMMETRICS "
                    + "or a custom namespace matching [A-Za-z][A-Za-z0-9_.]{2,63}");
        }

        // Rule 7: inventory_id format (only validated when provided)
        if (!Validations.isBlank(request.inventoryId())
                && !INVENTORY_ID_PATTERN.matcher(request.inventoryId()).matches()) {
            throw new InvalidParamException(
                    "inventory_id format invalid: '" + request.inventoryId()
                    + "'; expected resType_resId where resType is one of: "
                    + "host/application/instance/container/process/network/storage/volume");
        }
    }
}
