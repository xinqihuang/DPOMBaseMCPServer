/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.monitoring.cache.DiscoveryCacheConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApmDiscoveryService} 单元测试。
 *
 * <p>覆盖 T23 任务卡：基本校验 + Caffeine 缓存命中（同参数二次调用不打 adapter）+ 缓存
 * key 含 env_id（同 collectorId 不同 envId 不串号）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@SpringBootTest(classes = {ApmDiscoveryServiceTest.TestConfig.class, DiscoveryCacheConfig.class},
        properties = {"apm.discovery-cache.ttl=1h", "apm.discovery-cache.maximum-size=100"})
@Import(DiscoveryCacheConfig.class)
class ApmDiscoveryServiceTest {

    @MockBean
    private ApmDiscoveryAdapter adapter;

    @Autowired
    private ApmDiscoveryService service;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var c = cacheManager.getCache(name);
            if (c != null) {
                c.clear();
            }
        });
    }

    @Test
    @DisplayName("UT-D1: getEnvMonitorItems null envId -> INVALID_PARAM, adapter not called")
    void ut01NullEnv() {
        assertThatThrownBy(() -> service.getEnvMonitorItems(null, 7000L))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("env_id");
        verifyNoInteractions(adapter);
    }

    @Test
    @DisplayName("UT-D2: getEnvMonitorItems cached — same envId twice, adapter called once")
    void ut02EnvCacheHit() {
        ApmEnvMonitorItemsResponse resp = new ApmEnvMonitorItemsResponse(List.of(), List.of());
        when(adapter.showEnvMonitorItems(eq(1306682L), any())).thenReturn(resp);

        ApmEnvMonitorItemsResponse a = service.getEnvMonitorItems(1306682L, 7000L);
        ApmEnvMonitorItemsResponse b = service.getEnvMonitorItems(1306682L, 7000L);

        assertThat(a).isSameAs(resp);
        assertThat(b).isSameAs(resp);
        verify(adapter, times(1)).showEnvMonitorItems(eq(1306682L), any());
    }

    @Test
    @DisplayName("UT-D3: getMonitorItemViewConfig null params -> INVALID_PARAM")
    void ut03NullParams() {
        assertThatThrownBy(() -> service.getMonitorItemViewConfig(null, 1L, 7000L))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("collector_id");

        assertThatThrownBy(() -> service.getMonitorItemViewConfig(1L, null, 7000L))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("env_id");

        verifyNoInteractions(adapter);
    }

    @Test
    @DisplayName("UT-D4: view-config cache hit — same (env, collector) twice, adapter called once")
    void ut04ViewConfigCacheHit() {
        ApmMonitorItemViewConfigResponse resp = new ApmMonitorItemViewConfigResponse(
                "JVM", "JVM", List.of(), null);
        when(adapter.showMonitorItemViewConfig(eq(28L), eq(1306682L), any())).thenReturn(resp);

        service.getMonitorItemViewConfig(28L, 1306682L, 7000L);
        service.getMonitorItemViewConfig(28L, 1306682L, 7000L);

        verify(adapter, times(1)).showMonitorItemViewConfig(eq(28L), eq(1306682L), any());
    }

    @Test
    @DisplayName("UT-D5: view-config cache key includes envId — same collectorId different envId hits adapter twice")
    void ut05ViewConfigEnvIsolation() {
        ApmMonitorItemViewConfigResponse env1 = new ApmMonitorItemViewConfigResponse(
                "JVM", "JVM", List.of(), null);
        ApmMonitorItemViewConfigResponse env2 = new ApmMonitorItemViewConfigResponse(
                "Exception", "Exception", List.of(), null);
        when(adapter.showMonitorItemViewConfig(eq(18L), eq(1306682L), any())).thenReturn(env1);
        when(adapter.showMonitorItemViewConfig(eq(18L), eq(9999999L), any())).thenReturn(env2);

        ApmMonitorItemViewConfigResponse a = service.getMonitorItemViewConfig(18L, 1306682L, 7000L);
        ApmMonitorItemViewConfigResponse b = service.getMonitorItemViewConfig(18L, 9999999L, 7000L);

        assertThat(a).isSameAs(env1);
        assertThat(b).isSameAs(env2);
        verify(adapter, times(1)).showMonitorItemViewConfig(eq(18L), eq(1306682L), any());
        verify(adapter, times(1)).showMonitorItemViewConfig(eq(18L), eq(9999999L), any());
    }

    @org.springframework.boot.SpringBootConfiguration
    static class TestConfig {
        @Bean
        ApmDiscoveryService apmDiscoveryService(ApmDiscoveryAdapter adapter) {
            return new ApmDiscoveryService(adapter);
        }
    }
}
