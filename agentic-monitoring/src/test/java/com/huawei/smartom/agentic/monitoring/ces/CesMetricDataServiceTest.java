/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CesMetricDataService} 单元测试，重点覆盖 T24 的 SYS_RDS 形态 fallback 路由：
 * 主备实例不触发 fallback、集群实例透明改写 namespace、两 namespace 均无指标时明确报错。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class CesMetricDataServiceTest {

    private static final List<CesMetricDimension> DIMS =
            List.of(new CesMetricDimension("rds_instance_id", "rds-1"));
    private static final long FROM = 1700000000000L;
    private static final long TO = 1700003600000L;

    private CesMetricsAdapter adapter;
    private CesRdsNamespaceResolver resolver;
    private CesMetricDataService service;

    @BeforeEach
    void setUp() {
        adapter = mock(CesMetricsAdapter.class);
        resolver = mock(CesRdsNamespaceResolver.class);
        service = new CesMetricDataService(adapter, resolver);
    }

    @Test
    @DisplayName("non-RDS namespace bypasses the resolver and reaches the adapter unchanged")
    void nonRdsBypassesResolver() {
        CesQueryMetricDataRequest request = request("SYS.ECS");
        when(adapter.queryMetricData(request)).thenReturn(response("SYS.ECS"));

        CesQueryMetricDataResponse out = service.queryMetricData(request);

        assertThat(out.resolvedNamespace()).isEqualTo("SYS.ECS");
        verify(adapter).queryMetricData(request);
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("UT-RDS1: primary/standby instance resolves to SYS.RDS, request not rewritten")
    void rdsPrimaryNotRewritten() {
        when(resolver.resolve(DIMS.get(0))).thenReturn("SYS.RDS");
        CesQueryMetricDataRequest request = request("SYS.RDS");
        when(adapter.queryMetricData(request)).thenReturn(response("SYS.RDS"));

        CesQueryMetricDataResponse out = service.queryMetricData(request);

        assertThat(out.resolvedNamespace()).isEqualTo("SYS.RDS");
        verify(adapter).queryMetricData(request);
    }

    @Test
    @DisplayName("UT-RDS2: SYS.RDS without metrics falls back to SYS.RDS_MYSQL_CLUSTER")
    void rdsClusterFallbackRewritesNamespace() {
        when(resolver.resolve(DIMS.get(0))).thenReturn("SYS.RDS_MYSQL_CLUSTER");
        when(adapter.queryMetricData(any(CesQueryMetricDataRequest.class)))
                .thenReturn(response("SYS.RDS_MYSQL_CLUSTER"));

        CesQueryMetricDataResponse out = service.queryMetricData(request("SYS.RDS"));

        ArgumentCaptor<CesQueryMetricDataRequest> captor =
                ArgumentCaptor.forClass(CesQueryMetricDataRequest.class);
        verify(adapter).queryMetricData(captor.capture());
        CesQueryMetricDataRequest sent = captor.getValue();
        assertThat(sent.namespace()).isEqualTo("SYS.RDS_MYSQL_CLUSTER");
        assertThat(sent.metricName()).isEqualTo("cpu_util");
        assertThat(sent.dimensions()).isEqualTo(DIMS);
        assertThat(sent.filter()).isEqualTo(CesMetricFilter.AVERAGE);
        assertThat(out.resolvedNamespace()).isEqualTo("SYS.RDS_MYSQL_CLUSTER");
    }

    @Test
    @DisplayName("resolver rejection propagates as INVALID_PARAM, adapter never called")
    void resolverRejectionPropagates() {
        when(resolver.resolve(DIMS.get(0)))
                .thenThrow(new InvalidParamException("no metric definitions found"));

        assertThatThrownBy(() -> service.queryMetricData(request("SYS.RDS")))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).queryMetricData(any());
    }

    private static CesQueryMetricDataRequest request(String namespace) {
        return new CesQueryMetricDataRequest(
                namespace, "cpu_util", DIMS,
                CesMetricFilter.AVERAGE, CesMetricPeriod.MIN_5, FROM, TO);
    }

    private static CesQueryMetricDataResponse response(String resolvedNamespace) {
        return new CesQueryMetricDataResponse("cpu_util", List.of(), resolvedNamespace);
    }
}
