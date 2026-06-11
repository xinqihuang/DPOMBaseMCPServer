/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricQuery;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
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
 * {@link CesBatchMetricDataService} 单元测试，重点覆盖 T24 的逐项 SYS_RDS 形态 fallback：
 * 仅 SYS.RDS 查询项被探测改写，其余条目原样透传。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class CesBatchMetricDataServiceTest {

    private static final CesMetricDimension ECS_DIM = new CesMetricDimension("instance_id", "i-1");
    private static final CesMetricDimension RDS_DIM =
            new CesMetricDimension("rds_instance_id", "rds-1");
    private static final long FROM = 1700000000000L;
    private static final long TO = 1700003600000L;

    private CesMetricsAdapter adapter;
    private CesRdsNamespaceResolver resolver;
    private CesBatchMetricDataService service;

    @BeforeEach
    void setUp() {
        adapter = mock(CesMetricsAdapter.class);
        resolver = mock(CesRdsNamespaceResolver.class);
        service = new CesBatchMetricDataService(adapter, resolver);
    }

    @Test
    @DisplayName("batch without SYS.RDS items bypasses the resolver, request reused as-is")
    void noRdsItemsBypassesResolver() {
        CesBatchQueryMetricDataRequest request = request(List.of(
                new CesBatchMetricQuery("SYS.ECS", "cpu_util", List.of(ECS_DIM))));
        when(adapter.batchQueryMetricData(request))
                .thenReturn(new CesBatchQueryMetricDataResponse(List.of()));

        service.batchQueryMetricData(request);

        verify(adapter).batchQueryMetricData(request);
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("UT-RDS3: only the SYS.RDS item is rewritten to the cluster namespace")
    void mixedBatchRewritesOnlyRdsItem() {
        when(resolver.resolve(RDS_DIM)).thenReturn("SYS.RDS_MYSQL_CLUSTER");
        when(adapter.batchQueryMetricData(any(CesBatchQueryMetricDataRequest.class)))
                .thenReturn(new CesBatchQueryMetricDataResponse(List.of()));

        service.batchQueryMetricData(request(List.of(
                new CesBatchMetricQuery("SYS.ECS", "cpu_util", List.of(ECS_DIM)),
                new CesBatchMetricQuery("SYS.RDS", "rds001_cpu_util", List.of(RDS_DIM)))));

        ArgumentCaptor<CesBatchQueryMetricDataRequest> captor =
                ArgumentCaptor.forClass(CesBatchQueryMetricDataRequest.class);
        verify(adapter).batchQueryMetricData(captor.capture());
        List<CesBatchMetricQuery> sent = captor.getValue().metrics();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).namespace()).isEqualTo("SYS.ECS");
        assertThat(sent.get(1).namespace()).isEqualTo("SYS.RDS_MYSQL_CLUSTER");
        assertThat(sent.get(1).metricName()).isEqualTo("rds001_cpu_util");
        assertThat(sent.get(1).dimensions()).containsExactly(RDS_DIM);
    }

    @Test
    @DisplayName("SYS.RDS item resolving to SYS.RDS keeps the original request instance")
    void rdsPrimaryKeepsRequest() {
        when(resolver.resolve(RDS_DIM)).thenReturn("SYS.RDS");
        CesBatchQueryMetricDataRequest request = request(List.of(
                new CesBatchMetricQuery("SYS.RDS", "rds001_cpu_util", List.of(RDS_DIM))));
        when(adapter.batchQueryMetricData(request))
                .thenReturn(new CesBatchQueryMetricDataResponse(List.of()));

        service.batchQueryMetricData(request);

        verify(adapter).batchQueryMetricData(request);
    }

    @Test
    @DisplayName("resolver rejection propagates as INVALID_PARAM, adapter never called")
    void resolverRejectionPropagates() {
        when(resolver.resolve(RDS_DIM))
                .thenThrow(new InvalidParamException("no metric definitions found"));

        CesBatchQueryMetricDataRequest request = request(List.of(
                new CesBatchMetricQuery("SYS.RDS", "rds001_cpu_util", List.of(RDS_DIM))));
        assertThatThrownBy(() -> service.batchQueryMetricData(request))
                .isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).batchQueryMetricData(any());
    }

    private static CesBatchQueryMetricDataRequest request(List<CesBatchMetricQuery> metrics) {
        return new CesBatchQueryMetricDataRequest(
                metrics, CesMetricFilter.AVERAGE, CesMetricPeriod.MIN_5, FROM, TO);
    }
}
