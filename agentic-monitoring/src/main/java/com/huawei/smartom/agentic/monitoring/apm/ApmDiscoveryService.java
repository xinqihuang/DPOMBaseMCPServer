/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmListBusinessResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.monitoring.cache.DiscoveryCacheConfig;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * APM「监控项 / 视图配置发现」业务编排（薄层 + 缓存）。
 *
 * <p>本服务对应 T23 三步链路前两步，结果经 Caffeine 缓存（{@code expireAfterWrite=1d}，
 * 配置项 {@code apm.discovery-cache.ttl}）；趋势查询保持实时不缓存（见
 * {@link ApmTrendService}）。
 *
 * <p>缓存 key 设计：始终把 {@code env_id} 显式编入 key——同一 {@code collector_id} 在
 * 不同 env 含义不同，此为 T23 反复强调的核心约束（杜绝跨 env 复用 / 串号）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Service
public class ApmDiscoveryService {

    private final ApmDiscoveryAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code ApmDiscoveryService}。
     *
     * @param adapter APM 发现 adapter
     */
    public ApmDiscoveryService(ApmDiscoveryAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 查询当前租户的全部 APM 应用（business）列表；TTL 内复用缓存（应用目录稳定）。
     *
     * <p>空列表与 {@code null} 不写缓存，避免把临时性问题固化一整天。
     *
     * @return 应用列表，{@code id} 即后续工具的 {@code business_id}
     */
    @Cacheable(
            cacheNames = DiscoveryCacheConfig.CACHE_BUSINESS_LIST,
            unless = "#result == null or #result.businessNodes() == null"
                    + " or #result.businessNodes().isEmpty()")
    public ApmListBusinessResponse listBusiness() {
        return adapter.listBusiness();
    }

    /**
     * 搜索指定应用（business）下的组件与环境及其探针情况。
     *
     * <p><strong>不缓存</strong>：探针在线/离线计数是运行态信号，缓存会掩盖探针掉线。
     *
     * @param request 请求 DTO，不能为 null
     * @return 组件/环境列表与探针计数
     * @throws InvalidParamException {@code page} 或 {@code pageSize} 小于 1 时
     */
    public ApmSearchApplicationResponse searchApplication(ApmSearchApplicationRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        if (request.page() < 1) {
            throw new InvalidParamException("page must be >= 1, got: " + request.page());
        }
        if (request.pageSize() != null && request.pageSize() < 1) {
            throw new InvalidParamException("page_size must be >= 1, got: " + request.pageSize());
        }
        return adapter.searchApplication(request);
    }

    /**
     * 查询指定 env 下全部监控项与采集器类别；同参数复用 TTL 内的缓存结果。
     *
     * <p>结果为 {@code null} 时不写入缓存（{@code unless} 防止穿透）。
     *
     * @param envId      环境 id（必填）
     * @param businessId APM 业务 id；可为 {@code null}（adapter 端会回落到配置）
     * @return env 维度的监控项映射
     * @throws InvalidParamException {@code envId} 为空时
     */
    @Cacheable(
            cacheNames = DiscoveryCacheConfig.CACHE_ENV_ITEMS,
            key = "#envId",
            condition = "#envId != null",
            unless = "#result == null")
    public ApmEnvMonitorItemsResponse getEnvMonitorItems(Long envId, Long businessId) {
        if (envId == null) {
            throw new InvalidParamException("env_id is required");
        }
        return adapter.showEnvMonitorItems(envId, businessId);
    }

    /**
     * 查询某采集器在指定 env 下的视图清单；同 {@code (envId, collectorId)} 复用 TTL 内缓存。
     *
     * <p>缓存 key 形如 {@code "<envId>_<collectorId>"}，<b>必须</b>同时编入 env 与 collector
     * 才能避免跨 env 串号（{@code collector_id} 是 env 局部的）。
     *
     * @param collectorId 采集器 id（必填；env 局部）
     * @param envId       环境 id（必填）
     * @param businessId  APM 业务 id；可为 {@code null}（adapter 端会回落到配置）
     * @return 视图清单
     * @throws InvalidParamException {@code envId} 或 {@code collectorId} 为空时
     */
    @Cacheable(
            cacheNames = DiscoveryCacheConfig.CACHE_VIEW_CONFIG,
            key = "#envId + '_' + #collectorId",
            condition = "#envId != null and #collectorId != null",
            unless = "#result == null")
    public ApmMonitorItemViewConfigResponse getMonitorItemViewConfig(
            Long collectorId, Long envId, Long businessId) {
        if (envId == null) {
            throw new InvalidParamException("env_id is required");
        }
        if (collectorId == null) {
            throw new InvalidParamException("collector_id is required");
        }
        return adapter.showMonitorItemViewConfig(collectorId, envId, businessId);
    }
}
