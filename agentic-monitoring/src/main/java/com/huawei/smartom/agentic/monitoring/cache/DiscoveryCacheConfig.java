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
 * T23 「发现工具」缓存配置。
 *
 * <p>仅为 APM 发现工具的两个端点做堆内本地缓存（{@link #CACHE_ENV_ITEMS} /
 * {@link #CACHE_VIEW_CONFIG}），目的是缓存掉 MCP→华为云的 HTTP 往返；{@code show_apm_trend}
 * 需要实时数据，不在此处加缓存。
 *
 * <p>实现采用 Caffeine：
 * <ul>
 *   <li>{@code expireAfterWrite}：可由 {@code apm.discovery-cache.ttl} 调节，默认 1 天；</li>
 *   <li>{@code maximumSize}：防无界增长，默认 1000；</li>
 *   <li>缓存 key 由 service 层 SpEL 显式构造，始终带 {@code env_id} 前缀——避免将来多 env
 *       部署时跨 env 串号（{@code collector_id} 是 env 局部的）。</li>
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

    private final Duration ttl;
    private final long maximumSize;

    /**
     * 构造缓存配置。
     *
     * @param ttl         缓存写入后过期时间，默认 {@code 1d}
     * @param maximumSize 单缓存最大条目数，默认 {@code 1000}
     */
    public DiscoveryCacheConfig(
            @Value("${apm.discovery-cache.ttl:1d}") Duration ttl,
            @Value("${apm.discovery-cache.maximum-size:1000}") long maximumSize) {
        this.ttl = ttl;
        this.maximumSize = maximumSize;
    }

    /**
     * 注册 Caffeine 缓存管理器。
     *
     * @return 已配置 TTL 与容量上限的 {@link CaffeineCacheManager}
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_ENV_ITEMS, CACHE_VIEW_CONFIG);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize));
        return manager;
    }
}
