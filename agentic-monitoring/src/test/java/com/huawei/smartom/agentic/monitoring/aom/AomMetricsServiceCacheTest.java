/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricInfo;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPagination;
import com.huawei.smartom.agentic.monitoring.cache.DiscoveryCacheConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AomMetricsService} 的 Caffeine 缓存行为测试（T26）。
 *
 * <p>覆盖任务卡验收标准：同参数二次调用不打 adapter、空结果不写缓存、
 * 不同参数不串 key、evict 口生效、AOM 缓存与 CES 缓存相互独立（独立 cache 名 + 独立配置项）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@SpringBootTest(classes = {AomMetricsServiceCacheTest.TestConfig.class, DiscoveryCacheConfig.class},
        properties = {"aom.discovery-cache.ttl=30m", "aom.discovery-cache.maximum-size=100"})
class AomMetricsServiceCacheTest {

    @MockBean
    private AomMetricsAdapter adapter;

    @Autowired
    private AomMetricsService service;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Test
    @DisplayName("UT-C1: same request twice -> adapter invoked once (cache hit)")
    void sameRequestHitsCache() {
        when(adapter.listMetrics(any(AomListMetricsRequest.class)))
                .thenReturn(nonEmptyResponse());

        AomListMetricsResponse first = service.listMetrics(request("PAAS.CONTAINER", "cpuUsage"));
        AomListMetricsResponse second = service.listMetrics(request("PAAS.CONTAINER", "cpuUsage"));

        assertThat(second).isSameAs(first);
        verify(adapter, times(1)).listMetrics(any(AomListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C2: empty result is not cached -> adapter invoked twice")
    void emptyResultNotCached() {
        when(adapter.listMetrics(any(AomListMetricsRequest.class)))
                .thenReturn(new AomListMetricsResponse(
                        List.of(), new AomPagination(0, 0, null, null, false)));

        service.listMetrics(request("CUSTOMMETRICS", null));
        service.listMetrics(request("CUSTOMMETRICS", null));

        verify(adapter, times(2)).listMetrics(any(AomListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C3: different parameters use different cache keys")
    void differentParamsDifferentKeys() {
        when(adapter.listMetrics(any(AomListMetricsRequest.class)))
                .thenReturn(nonEmptyResponse());

        service.listMetrics(request("PAAS.CONTAINER", "cpuUsage"));
        service.listMetrics(request("PAAS.CONTAINER", "memUsage"));

        verify(adapter, times(2)).listMetrics(any(AomListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C4: evictListMetricsCache invalidates all cached entries")
    void evictClearsCache() {
        when(adapter.listMetrics(any(AomListMetricsRequest.class)))
                .thenReturn(nonEmptyResponse());

        service.listMetrics(request("PAAS.NODE", "cpuUsage"));
        service.evictListMetricsCache();
        service.listMetrics(request("PAAS.NODE", "cpuUsage"));

        verify(adapter, times(2)).listMetrics(any(AomListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C5: AOM cache name is registered independently from the CES cache")
    void aomCacheRegisteredIndependently() {
        assertThat(cacheManager.getCacheNames())
                .contains(DiscoveryCacheConfig.CACHE_AOM_METRICS, DiscoveryCacheConfig.CACHE_CES_METRICS);
        assertThat(DiscoveryCacheConfig.CACHE_AOM_METRICS)
                .isNotEqualTo(DiscoveryCacheConfig.CACHE_CES_METRICS);
    }

    private static AomListMetricsRequest request(String namespace, String metricName) {
        return new AomListMetricsRequest(namespace, metricName, null, null, null, null);
    }

    private static AomListMetricsResponse nonEmptyResponse() {
        return new AomListMetricsResponse(
                List.of(new AomMetricInfo("PAAS.CONTAINER", "cpuUsage", "Percent", List.of(), null)),
                new AomPagination(1, 1, 0, null, false));
    }

    /**
     * 测试装配：注册被测 service 的最小 Bean 集。
     */
    @Configuration
    static class TestConfig {
        @Bean
        AomMetricsService aomMetricsService(AomMetricsAdapter adapter) {
            return new AomMetricsService(adapter);
        }
    }
}
