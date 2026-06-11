/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPagination;
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
 * {@link CesMetricsService} 的 Caffeine 缓存行为测试（T24）。
 *
 * <p>覆盖任务卡验收标准：同参数二次调用不打 adapter、空结果不写缓存、
 * 不同参数不串 key、evict 口生效。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@SpringBootTest(classes = {CesMetricsServiceCacheTest.TestConfig.class, DiscoveryCacheConfig.class},
        properties = {"ces.discovery-cache.ttl=1h", "ces.discovery-cache.maximum-size=100"})
class CesMetricsServiceCacheTest {

    @MockBean
    private CesMetricsAdapter adapter;

    @Autowired
    private CesMetricsService service;

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
        when(adapter.listMetrics(any(CesListMetricsRequest.class)))
                .thenReturn(nonEmptyResponse());

        CesListMetricsResponse first = service.listMetrics(request("SYS.ECS", "cpu_util"));
        CesListMetricsResponse second = service.listMetrics(request("SYS.ECS", "cpu_util"));

        assertThat(second).isSameAs(first);
        verify(adapter, times(1)).listMetrics(any(CesListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C2: empty result is not cached -> adapter invoked twice")
    void emptyResultNotCached() {
        when(adapter.listMetrics(any(CesListMetricsRequest.class)))
                .thenReturn(new CesListMetricsResponse(List.of(), new CesPagination(0, 0, null, false)));

        service.listMetrics(request("SYS.RDS", null));
        service.listMetrics(request("SYS.RDS", null));

        verify(adapter, times(2)).listMetrics(any(CesListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C3: different parameters use different cache keys")
    void differentParamsDifferentKeys() {
        when(adapter.listMetrics(any(CesListMetricsRequest.class)))
                .thenReturn(nonEmptyResponse());

        service.listMetrics(request("SYS.ECS", "cpu_util"));
        service.listMetrics(request("SYS.ECS", "mem_util"));

        verify(adapter, times(2)).listMetrics(any(CesListMetricsRequest.class));
    }

    @Test
    @DisplayName("UT-C4: evictListMetricsCache invalidates all cached entries")
    void evictClearsCache() {
        when(adapter.listMetrics(any(CesListMetricsRequest.class)))
                .thenReturn(nonEmptyResponse());

        service.listMetrics(request("SYS.ECS", "cpu_util"));
        service.evictListMetricsCache();
        service.listMetrics(request("SYS.ECS", "cpu_util"));

        verify(adapter, times(2)).listMetrics(any(CesListMetricsRequest.class));
    }

    private static CesListMetricsRequest request(String namespace, String metricName) {
        return new CesListMetricsRequest(namespace, metricName, null, null, null, null, null);
    }

    private static CesListMetricsResponse nonEmptyResponse() {
        return new CesListMetricsResponse(
                List.of(new CesMetricInfo("SYS.ECS", "cpu_util", "%", List.of())),
                new CesPagination(1, 1, null, false));
    }

    /**
     * 测试装配：注册被测 service 的最小 Bean 集。
     */
    @Configuration
    static class TestConfig {
        @Bean
        CesMetricsService cesMetricsService(CesMetricsAdapter adapter) {
            return new CesMetricsService(adapter);
        }
    }
}
