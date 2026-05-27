/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricInfo;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPagination;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.Dimension;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsResponse;
import com.huaweicloud.sdk.aom.v2.model.MetaDataSeries;
import com.huaweicloud.sdk.aom.v2.model.MetricAPIQueryItemParam;
import com.huaweicloud.sdk.aom.v2.model.MetricItemResultAPI;
import com.huaweicloud.sdk.aom.v2.model.QueryMetricItemOptionParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于华为云 Java SDK v3.1.177 实现的 {@link AomMetricsAdapter} 默认实现。
 *
 * <p>根据入参不同存在两条调用路径：
 * <ul>
 *   <li>路径 A（inventoryId）：{@code type=inventory}，请求体仅携带 inventoryId。</li>
 *   <li>路径 B（namespace）：请求体携带 {@code metricItems} 列表，包含 namespace/metricName/dims。</li>
 * </ul>
 * 当同时提供 inventoryId 与 namespace 时，使用路径 A 并打印 WARN 日志。
 * SDK 异常会通过 {@link HuaweiCloudInvocation} 统一转换为 SmartomException。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class AomMetricsAdapterImpl implements AomMetricsAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(AomMetricsAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "aom-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_ID = "aom.listMetricItems";

    private final AomClient aomClient;
    private final HuaweiCloudInvocation invocation;

    /**
     * 构造 {@code AomMetricsAdapterImpl}，注入华为云 AOM SDK 客户端以及共享的容错与异常映射调用助手。
     *
     * @param aomClient  已配置好的华为云 AOM SDK 客户端
     * @param invocation 提供限流、重试以及 SDK 异常映射能力的调用助手
     */
    public AomMetricsAdapterImpl(AomClient aomClient, HuaweiCloudInvocation invocation) {
        this.aomClient = aomClient;
        this.invocation = invocation;
    }

    @Override
    public AomListMetricsResponse listMetrics(AomListMetricsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("aom.listMetricItems start, namespace={}, inventoryId={}, limit={}",
                request.namespace(), request.inventoryId(), request.limit());

        ListMetricItemsRequest sdkRequest = toSdkRequest(request);
        ListMetricItemsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_ID,
                () -> aomClient.listMetricItems(sdkRequest));

        return toResponseDto(sdkResponse);
    }

    /**
     * 构造 SDK 请求对象。路径 A 使用 {@code type=inventory}；路径 B 在请求体中携带 metricItems。
     * 当 inventoryId 存在时优先于 namespace。
     *
     * @param request 已校验的输入 DTO
     * @return 已填充完毕、可直接发送的 SDK {@link ListMetricItemsRequest}
     */
    private ListMetricItemsRequest toSdkRequest(AomListMetricsRequest request) {
        if (request.inventoryId() != null) {
            if (request.namespace() != null) {
                LOG.warn("aom.listMetricItems: both inventory_id and namespace provided; "
                        + "inventory_id takes precedence, namespace ignored");
            }
            return new ListMetricItemsRequest()
                    .withType("inventory")
                    .withLimit(String.valueOf(request.limit()))
                    .withStart(String.valueOf(request.start()))
                    .withBody(new MetricAPIQueryItemParam()
                            .withInventoryId(request.inventoryId()));
        }

        // Path B: namespace-based query
        QueryMetricItemOptionParam item = new QueryMetricItemOptionParam()
                .withNamespace(QueryMetricItemOptionParam.NamespaceEnum.fromValue(request.namespace()));
        if (request.metricName() != null) {
            item.setMetricName(request.metricName());
        }
        if (request.dimensions() != null && !request.dimensions().isEmpty()) {
            List<Dimension> sdkDims = request.dimensions().stream()
                    .map(dim -> new Dimension().withName(dim.name()).withValue(dim.value()))
                    .toList();
            item.setDimensions(sdkDims);
        }
        return new ListMetricItemsRequest()
                .withLimit(String.valueOf(request.limit()))
                .withStart(String.valueOf(request.start()))
                .withBody(new MetricAPIQueryItemParam()
                        .withMetricItems(List.of(item)));
    }

    private AomListMetricsResponse toResponseDto(ListMetricItemsResponse sdkResp) {
        List<MetricItemResultAPI> sdkMetrics =
                sdkResp.getMetrics() == null ? Collections.emptyList() : sdkResp.getMetrics();
        List<AomMetricInfo> metrics = sdkMetrics.stream()
                .map(this::toMetricInfo)
                .toList();

        MetaDataSeries meta = sdkResp.getMetaData();
        int count;
        int total;
        Integer offset;
        Integer nextToken;
        if (meta == null) {
            count = metrics.size();
            total = metrics.size();
            offset = null;
            nextToken = null;
        } else {
            count = meta.getCount() == null ? metrics.size() : meta.getCount();
            total = meta.getTotal() == null ? metrics.size() : meta.getTotal();
            offset = meta.getOffset();
            nextToken = meta.getNextToken();
        }
        boolean hasMore = nextToken != null && count > 0;

        return new AomListMetricsResponse(
                metrics,
                new AomPagination(count, total, offset, nextToken, hasMore));
    }

    private AomMetricInfo toMetricInfo(MetricItemResultAPI sdkMetric) {
        List<Dimension> sdkDims =
                sdkMetric.getDimensions() == null ? Collections.emptyList() : sdkMetric.getDimensions();
        List<AomMetricDimension> dims = sdkDims.stream()
                .map(dim -> new AomMetricDimension(dim.getName(), dim.getValue()))
                .toList();
        return new AomMetricInfo(
                sdkMetric.getNamespace(),
                sdkMetric.getMetricName(),
                sdkMetric.getUnit(),
                dims,
                sdkMetric.getDimensionvaluehash());
    }
}
