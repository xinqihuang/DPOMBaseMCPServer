/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
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
 * 基于华为云 Java SDK v3.1.196 的 {@link CesMetricsAdapter} 默认实现。
 *
 * <p>负责在对外 DTO 与 SDK 请求／响应类之间进行映射。所有 SDK 异常都通过
 * {@link HuaweiCloudInvocation} 统一转换为 {@code SmartomException}。
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
     * 构造 {@code CesMetricsAdapterImpl}，注入华为云 CES SDK 客户端，以及统一的容错与异常映射调用帮助类。
     *
     * @param cesClient  已配置的华为云 CES SDK 客户端
     * @param invocation 负责限流、重试以及 SDK 异常映射的调用帮助类
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
     * 构建 SDK 请求对象。为 null 的字段保持未设置状态；SDK 采用 {@code NON_NULL} 策略进行序列化，
     * 因此缺省字段不会出现在请求报文中。
     *
     * @param request 已校验的入参 DTO
     * @return 填充完毕、可直接下发的 SDK {@link ListMetricsRequest} 对象
     */
    private ListMetricsRequest toSdkRequest(CesListMetricsRequest request) {
        ListMetricsRequest sdk = new ListMetricsRequest();
        if (request.namespace() != null) {
            sdk.setNamespace(request.namespace());
        }
        if (request.metricName() != null) {
            sdk.setMetricName(request.metricName());
        }
        if (request.dimName() != null && request.dimValue() != null) {
            sdk.setDim0(request.dimName() + "," + request.dimValue());
        }
        sdk.setLimit(request.limit());
        if (request.start() != null) {
            sdk.setStart(request.start());
        }
        // order is normalised to "asc"/"desc" upstream; map to SDK enum.
        sdk.setOrder(toOrderEnum(request.order()));
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
        }
        else {
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
                .map(dim -> new CesMetricDimension(dim.getName(), dim.getValue()))
                .toList();
        return new CesMetricInfo(
                sdkMetric.getNamespace(),
                sdkMetric.getMetricName(),
                sdkMetric.getUnit(),
                dims);
    }
}
