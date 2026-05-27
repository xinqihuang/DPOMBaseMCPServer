/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPagination;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsRequest;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsResponse;
import com.huaweicloud.sdk.ces.v1.model.MetaData;
import com.huaweicloud.sdk.ces.v1.model.MetricInfoList;
import com.huaweicloud.sdk.ces.v1.model.MetricsDimension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default {@link CesMetricsAdapter} implementation backed by Huawei Cloud Java SDK v3.1.196.
 *
 * <p>Maps between our public DTOs and the SDK request/response classes. All SDK exceptions
 * are funnelled through {@link HuaweiCloudInvocation} to be mapped into {@code SmartomException}.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class CesMetricsAdapterImpl implements CesMetricsAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CesMetricsAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "ces-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_ID = "ces.listMetrics";

    private final CesClient cesClient;
    private final HuaweiCloudInvocation invocation;

    /**
     * Constructs a new {@code CesMetricsAdapterImpl} wired with the Huawei Cloud CES SDK client
     * and the shared resilience/exception-mapping invocation helper.
     *
     * @param cesClient  configured Huawei Cloud CES SDK client
     * @param invocation helper applying rate limiting, retry and SDK exception mapping
     */
    public CesMetricsAdapterImpl(CesClient cesClient, HuaweiCloudInvocation invocation) {
        this.cesClient = cesClient;
        this.invocation = invocation;
    }

    @Override
    public CesListMetricsResponse listMetrics(CesListMetricsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("ces.listMetrics start, namespace={}, metricName={}, dimName={}, limit={}",
                request.namespace(), request.metricName(), request.dimName(), request.limit());

        ListMetricsRequest sdkRequest = toSdkRequest(request);
        ListMetricsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_ID,
                () -> cesClient.listMetrics(sdkRequest));

        return toResponseDto(sdkResponse);
    }

    /**
     * Build the SDK request. Null fields are left unset; the SDK serialises with
     * {@code NON_NULL} inclusion so absent fields will not appear on the wire.
     */
    private ListMetricsRequest toSdkRequest(CesListMetricsRequest r) {
        ListMetricsRequest sdk = new ListMetricsRequest();
        if (r.namespace() != null) {
            sdk.setNamespace(r.namespace());
        }
        if (r.metricName() != null) {
            sdk.setMetricName(r.metricName());
        }
        if (r.dimName() != null && r.dimValue() != null) {
            sdk.setDim0(r.dimName() + "," + r.dimValue());
        }
        sdk.setLimit(r.limit());
        if (r.start() != null) {
            sdk.setStart(r.start());
        }
        // order is normalised to "asc"/"desc" upstream; map to SDK enum.
        sdk.setOrder(toOrderEnum(r.order()));
        return sdk;
    }

    private ListMetricsRequest.OrderEnum toOrderEnum(String order) {
        if ("asc".equals(order)) {
            return ListMetricsRequest.OrderEnum.ASC;
        }
        return ListMetricsRequest.OrderEnum.DESC;
    }

    private CesListMetricsResponse toResponseDto(ListMetricsResponse sdkResp) {
        List<MetricInfoList> sdkMetrics =
                sdkResp.getMetrics() == null ? Collections.emptyList() : sdkResp.getMetrics();
        List<CesMetricInfo> metrics = sdkMetrics.stream()
                .map(this::toMetricInfo)
                .toList();

        MetaData meta = sdkResp.getMetaData();
        int count;
        int total;
        String nextMarker;
        if (meta == null) {
            count = metrics.size();
            total = metrics.size();
            nextMarker = null;
        } else {
            count = meta.getCount() == null ? metrics.size() : meta.getCount();
            total = meta.getTotal() == null ? metrics.size() : meta.getTotal();
            nextMarker = meta.getMarker();
        }
        boolean hasMore = nextMarker != null && count > 0;

        return new CesListMetricsResponse(
                metrics,
                new CesPagination(count, total, nextMarker, hasMore));
    }

    private CesMetricInfo toMetricInfo(MetricInfoList sdkMetric) {
        List<MetricsDimension> sdkDims =
                sdkMetric.getDimensions() == null ? Collections.emptyList() : sdkMetric.getDimensions();
        List<CesMetricDimension> dims = sdkDims.stream()
                .map(d -> new CesMetricDimension(d.getName(), d.getValue()))
                .toList();
        return new CesMetricInfo(
                sdkMetric.getNamespace(),
                sdkMetric.getMetricName(),
                sdkMetric.getUnit(),
                dims);
    }
}
