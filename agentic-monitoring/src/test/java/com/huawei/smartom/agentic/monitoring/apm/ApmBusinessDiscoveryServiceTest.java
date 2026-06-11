/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmDiscoveryAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmBusinessNode;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmListBusinessResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse;
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
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApmDiscoveryService} 中 T28 新增方法（listBusiness / searchApplication）的单元测试：
 * 应用列表缓存命中、空列表不缓存、search 分页校验与不缓存语义。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@SpringBootTest(
        classes = {ApmBusinessDiscoveryServiceTest.TestConfig.class, DiscoveryCacheConfig.class},
        properties = {"apm.discovery-cache.ttl=1h", "apm.discovery-cache.maximum-size=100"})
class ApmBusinessDiscoveryServiceTest {

    @MockBean
    private ApmDiscoveryAdapter adapter;

    @Autowired
    private ApmDiscoveryService service;

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
    @DisplayName("UT-B1: listBusiness second call hits the cache (adapter invoked once)")
    void listBusinessCached() {
        when(adapter.listBusiness()).thenReturn(nonEmptyBusinessList());

        ApmListBusinessResponse first = service.listBusiness();
        ApmListBusinessResponse second = service.listBusiness();

        assertThat(second).isSameAs(first);
        verify(adapter, times(1)).listBusiness();
    }

    @Test
    @DisplayName("UT-B2: empty business list is not cached (adapter invoked twice)")
    void emptyBusinessListNotCached() {
        when(adapter.listBusiness()).thenReturn(new ApmListBusinessResponse(List.of()));

        service.listBusiness();
        service.listBusiness();

        verify(adapter, times(2)).listBusiness();
    }

    @Test
    @DisplayName("UT-B3: searchApplication is never cached (adapter invoked per call)")
    void searchApplicationNotCached() {
        ApmSearchApplicationRequest request =
                new ApmSearchApplicationRequest(7000L, "cn-north-4", 1, 100, null);
        when(adapter.searchApplication(request))
                .thenReturn(new ApmSearchApplicationResponse(List.of(), 0, null));

        service.searchApplication(request);
        service.searchApplication(request);

        verify(adapter, times(2)).searchApplication(request);
    }

    @Test
    @DisplayName("UT-B4: page/page_size below 1 -> INVALID_PARAM, adapter not called")
    void invalidPagingRejected() {
        assertThatThrownBy(() -> service.searchApplication(
                new ApmSearchApplicationRequest(7000L, null, 0, null, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> service.searchApplication(
                new ApmSearchApplicationRequest(7000L, null, 1, 0, null)))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("page_size");
        assertThatThrownBy(() -> service.searchApplication(null))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).searchApplication(any());
    }

    private static ApmListBusinessResponse nonEmptyBusinessList() {
        return new ApmListBusinessResponse(List.of(new ApmBusinessNode(
                Boolean.FALSE, "订单中心", "0", null, null, 7000L, 12345, Boolean.FALSE,
                "order-center")));
    }

    /**
     * 测试装配：注册被测 service 的最小 Bean 集。
     */
    @Configuration
    static class TestConfig {
        @Bean
        ApmDiscoveryService apmDiscoveryService(ApmDiscoveryAdapter adapter) {
            return new ApmDiscoveryService(adapter);
        }
    }
}
