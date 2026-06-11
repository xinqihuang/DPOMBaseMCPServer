/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPagination;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CesRdsNamespaceResolver} 单元测试（T24 SYS_RDS 形态 fallback 的探测逻辑）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class CesRdsNamespaceResolverTest {

    private static final CesMetricDimension DIM =
            new CesMetricDimension("rds_instance_id", "rds-1");

    private CesMetricsService metricsService;
    private CesRdsNamespaceResolver resolver;

    @BeforeEach
    void setUp() {
        metricsService = mock(CesMetricsService.class);
        resolver = new CesRdsNamespaceResolver(metricsService);
    }

    @Test
    @DisplayName("appliesTo is true only for SYS.RDS")
    void appliesToOnlySysRds() {
        assertThat(CesRdsNamespaceResolver.appliesTo("SYS.RDS")).isTrue();
        assertThat(CesRdsNamespaceResolver.appliesTo("SYS.ECS")).isFalse();
        assertThat(CesRdsNamespaceResolver.appliesTo("SYS.RDS_MYSQL_CLUSTER")).isFalse();
        assertThat(CesRdsNamespaceResolver.appliesTo(null)).isFalse();
    }

    @Test
    @DisplayName("primary/standby instance hits SYS.RDS, cluster namespace is never probed")
    void primaryInstanceResolvesToSysRds() {
        when(metricsService.listMetrics(probeRequest("SYS.RDS")))
                .thenReturn(nonEmptyResponse("SYS.RDS"));

        assertThat(resolver.resolve(DIM)).isEqualTo("SYS.RDS");

        verify(metricsService, never()).listMetrics(probeRequest("SYS.RDS_MYSQL_CLUSTER"));
    }

    @Test
    @DisplayName("SYS.RDS empty -> probes cluster namespace and resolves to SYS.RDS_MYSQL_CLUSTER")
    void clusterInstanceFallsBack() {
        when(metricsService.listMetrics(probeRequest("SYS.RDS")))
                .thenReturn(emptyResponse());
        when(metricsService.listMetrics(probeRequest("SYS.RDS_MYSQL_CLUSTER")))
                .thenReturn(nonEmptyResponse("SYS.RDS_MYSQL_CLUSTER"));

        assertThat(resolver.resolve(DIM)).isEqualTo("SYS.RDS_MYSQL_CLUSTER");
    }

    @Test
    @DisplayName("no metrics under either namespace -> INVALID_PARAM naming both namespaces")
    void bothNamespacesEmptyRejected() {
        when(metricsService.listMetrics(any(CesListMetricsRequest.class)))
                .thenReturn(emptyResponse());

        assertThatThrownBy(() -> resolver.resolve(DIM))
                .isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("SYS.RDS")
                .hasMessageContaining("SYS.RDS_MYSQL_CLUSTER")
                .hasMessageContaining("rds-1");
    }

    @Test
    @DisplayName("null dimension -> INVALID_PARAM without probing")
    void nullDimensionRejected() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(InvalidParamException.class);
        verify(metricsService, never()).listMetrics(any());
    }

    private static CesListMetricsRequest probeRequest(String namespace) {
        return new CesListMetricsRequest(namespace, null, DIM.name(), DIM.value(), 1, null, null);
    }

    private static CesListMetricsResponse nonEmptyResponse(String namespace) {
        return new CesListMetricsResponse(
                List.of(new CesMetricInfo(namespace, "cpu_util", "%", List.of(DIM))),
                new CesPagination(1, 1, null, false));
    }

    private static CesListMetricsResponse emptyResponse() {
        return new CesListMetricsResponse(List.of(), new CesPagination(0, 0, null, false));
    }
}
