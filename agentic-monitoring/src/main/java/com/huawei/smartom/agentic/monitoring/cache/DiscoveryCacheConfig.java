/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.cache;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 「发现工具」缓存配置（T23 APM + T24 CES + T26 AOM）。
 *
 * <p>为发现类只读端点做堆内本地缓存，目的是缓存掉 MCP→华为云的 HTTP 往返：
 * <ul>
 *   <li>APM：{@link #CACHE_ENV_ITEMS} / {@link #CACHE_VIEW_CONFIG}（T23）；</li>
 *   <li>CES：{@link #CACHE_CES_METRICS}，即 {@code list_ces_metrics} 的指标定义目录（T24）。
 *       Agent 也会用它探测 RDS 实例的命名空间归属，缓存使重复探测几乎零成本；</li>
 *   <li>AOM：{@link #CACHE_AOM_METRICS}，即 {@code list_aom_metrics} 的指标定义目录（T26）。</li>
 * </ul>
 * 实时数据端点（{@code show_apm_trend}、CES/AOM 指标数据查询、日志与告警查询）不在此处加缓存。
 *
 * <p>实现采用 Caffeine：
 * <ul>
 *   <li>{@code expireAfterWrite}：APM 由 {@code apm.discovery-cache.ttl} 调节（默认 1d），
 *       CES 由 {@code ces.discovery-cache.ttl} 调节（默认 1d），AOM 由
 *       {@code aom.discovery-cache.ttl} 调节（默认 <strong>1h</strong>——AOM 清单含
 *       容器/进程级对象，churn 快于 CES 云服务目录，1d 会服务陈旧清单）；</li>
 *   <li>{@code maximumSize}：防无界增长，APM 默认 1000，CES/AOM 默认 2000
 *       （缓存 key 含全部查询参数，组合空间更大）；</li>
 *   <li>缓存 key 由 service 层显式构造：APM 始终带 {@code env_id} 前缀防跨 env 串号；
 *       CES/AOM 直接以请求 record 为 key（值语义 equals 覆盖全部参数）。</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Configuration
@EnableCaching
public class DiscoveryCacheConfig {

    /** {@code show_env_monitor_items} 结果缓存名。 */
    public static final String CACHE_ENV_ITEMS = "apm-env-monitor-items";

    /** {@code show_apm_monitor_item_view_config} 结果缓存名。 */
    public static final String CACHE_VIEW_CONFIG = "apm-monitor-item-view-config";

    /** {@code list_apm_business} 结果缓存名（T28；应用目录稳定，共享 APM 默认 spec）。 */
    public static final String CACHE_BUSINESS_LIST = "apm-business-list";

    /** {@code list_ces_metrics} 结果缓存名（T24）。 */
    public static final String CACHE_CES_METRICS = "ces-list-metrics";

    /** {@code list_aom_metrics} 结果缓存名（T26）。 */
    public static final String CACHE_AOM_METRICS = "aom-list-metrics";

    private final Duration ttl;
    private final long maximumSize;
    private final Duration cesTtl;
    private final long cesMaximumSize;
    private final Duration aomTtl;
    private final long aomMaximumSize;

    /**
     * 构造缓存配置。
     *
     * @param ttl            APM 缓存写入后过期时间，默认 {@code 1d}
     * @param maximumSize    APM 单缓存最大条目数，默认 {@code 1000}
     * @param cesTtl         CES 缓存写入后过期时间，默认 {@code 1d}
     * @param cesMaximumSize CES 缓存最大条目数，默认 {@code 2000}
     * @param aomTtl         AOM 缓存写入后过期时间，默认 {@code 1h}
     * @param aomMaximumSize AOM 缓存最大条目数，默认 {@code 2000}
     */
    public DiscoveryCacheConfig(
            @Value("${apm.discovery-cache.ttl:1d}") Duration ttl,
            @Value("${apm.discovery-cache.maximum-size:1000}") long maximumSize,
            @Value("${ces.discovery-cache.ttl:1d}") Duration cesTtl,
            @Value("${ces.discovery-cache.maximum-size:2000}") long cesMaximumSize,
            @Value("${aom.discovery-cache.ttl:1h}") Duration aomTtl,
            @Value("${aom.discovery-cache.maximum-size:2000}") long aomMaximumSize) {
        this.ttl = ttl;
        this.maximumSize = maximumSize;
        this.cesTtl = cesTtl;
        this.cesMaximumSize = cesMaximumSize;
        this.aomTtl = aomTtl;
        this.aomMaximumSize = aomMaximumSize;
    }

    /**
     * 注册 Caffeine 缓存管理器。APM 两个缓存共享默认 spec，CES / AOM 缓存以独立 spec 注册，
     * 便于按服务单独调节 TTL 与容量。
     *
     * @return 已配置 TTL 与容量上限的 {@link CaffeineCacheManager}
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager =
                new CaffeineCacheManager(CACHE_ENV_ITEMS, CACHE_VIEW_CONFIG, CACHE_BUSINESS_LIST);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize));
        manager.registerCustomCache(CACHE_CES_METRICS, Caffeine.newBuilder()
                .expireAfterWrite(cesTtl)
                .maximumSize(cesMaximumSize)
                .build());
        manager.registerCustomCache(CACHE_AOM_METRICS, Caffeine.newBuilder()
                .expireAfterWrite(aomTtl)
                .maximumSize(aomMaximumSize)
                .build());
        return manager;
    }
}
