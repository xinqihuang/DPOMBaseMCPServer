/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDatapoint;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricInfo;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricStatistic;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPagination;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomSampleSeries;
import com.huawei.smartom.agentic.adapter.aom.dto.AomStatisticValue;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.Dimension;
import com.huaweicloud.sdk.aom.v2.model.DimensionSeries;
import com.huaweicloud.sdk.aom.v2.model.ListLogItemsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListLogItemsResponse;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsResponse;
import com.huaweicloud.sdk.aom.v2.model.ListSampleRequest;
import com.huaweicloud.sdk.aom.v2.model.ListSampleResponse;
import com.huaweicloud.sdk.aom.v2.model.MetaDataSeries;
import com.huaweicloud.sdk.aom.v2.model.MetricAPIQueryItemParam;
import com.huaweicloud.sdk.aom.v2.model.MetricDataPoints;
import com.huaweicloud.sdk.aom.v2.model.MetricItemResultAPI;
import com.huaweicloud.sdk.aom.v2.model.QueryBodyParam;
import com.huaweicloud.sdk.aom.v2.model.QueryMetricItemOptionParam;
import com.huaweicloud.sdk.aom.v2.model.QuerySample;
import com.huaweicloud.sdk.aom.v2.model.QuerySampleParam;
import com.huaweicloud.sdk.aom.v2.model.SampleDataValue;
import com.huaweicloud.sdk.aom.v2.model.StatisticValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于华为云 Java SDK v3.1.177 的 {@link AomMetricsAdapter} 实现。
 *
 * <p>本类同时承载 {@code listMetrics}、{@code queryMetricData}（ListSample）以及
 * {@code queryLogs}（ListLogItems）三组能力。所有 SDK 异常通过
 * {@link HuaweiCloudInvocation} 统一映射为 {@code SmartomException}。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class AomMetricsAdapterImpl implements AomMetricsAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(AomMetricsAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "aom-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_LIST_METRICS = "aom.listMetricItems";
    private static final String API_LIST_SAMPLE = "aom.listSample";
    private static final String API_LIST_LOG_ITEMS = "aom.listLogItems";

    private final AomClient aomClient;
    private final HuaweiCloudInvocation invocation;

    /**
     * 构造 {@code AomMetricsAdapterImpl}。
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

        ListMetricItemsRequest sdkRequest = toListMetricItemsSdkRequest(request);
        ListMetricItemsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_METRICS,
                () -> aomClient.listMetricItems(sdkRequest));

        return toListMetricsResponseDto(sdkResponse);
    }

    @Override
    public AomQueryMetricDataResponse queryMetricData(AomQueryMetricDataRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("aom.listSample start, namespace={}, metricName={}, period={}, timeRange={}",
                request.namespace(), request.metricName(), request.period(), request.timeRange());

        ListSampleRequest sdkRequest = toListSampleSdkRequest(request);
        ListSampleResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_SAMPLE,
                () -> aomClient.listSample(sdkRequest));

        return toQueryMetricDataResponseDto(sdkResponse);
    }

    @Override
    public AomQueryLogsResponse queryLogs(AomQueryLogsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("aom.listLogItems start, category={}, startTime={}, endTime={}, pageSize={}",
                request.category(), request.startTime(), request.endTime(), request.pageSize());

        ListLogItemsRequest sdkRequest = toListLogItemsSdkRequest(request);
        ListLogItemsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_LOG_ITEMS,
                () -> aomClient.listLogItems(sdkRequest));

        return new AomQueryLogsResponse(
                sdkResponse.getResult(),
                sdkResponse.getErrorCode(),
                sdkResponse.getErrorMessage());
    }

    private ListMetricItemsRequest toListMetricItemsSdkRequest(AomListMetricsRequest request) {
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

    private AomListMetricsResponse toListMetricsResponseDto(ListMetricItemsResponse sdkResp) {
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
        }
        else {
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

    private ListSampleRequest toListSampleSdkRequest(AomQueryMetricDataRequest request) {
        QuerySample sample = new QuerySample()
                .withNamespace(request.namespace())
                .withMetricName(request.metricName());
        if (request.dimensions() != null && !request.dimensions().isEmpty()) {
            List<DimensionSeries> dims = request.dimensions().stream()
                    .map(dim -> new DimensionSeries().withName(dim.name()).withValue(dim.value()))
                    .toList();
            sample.setDimensions(dims);
        }
        QuerySampleParam body = new QuerySampleParam()
                .withSamples(List.of(sample))
                .withPeriod(request.period() == null ? null : request.period().getSeconds())
                .withTimeRange(request.timeRange());
        if (request.statistics() != null && !request.statistics().isEmpty()) {
            body.setStatistics(request.statistics().stream()
                    .map(AomMetricStatistic::getValue)
                    .toList());
        }

        ListSampleRequest sdk = new ListSampleRequest().withBody(body);
        if (request.fillValue() != null) {
            sdk.setFillValue(request.fillValue().getValue());
        }
        return sdk;
    }

    private AomQueryMetricDataResponse toQueryMetricDataResponseDto(ListSampleResponse sdkResp) {
        List<SampleDataValue> sdkSamples =
                sdkResp.getSamples() == null ? Collections.emptyList() : sdkResp.getSamples();
        List<AomSampleSeries> series = sdkSamples.stream()
                .map(this::toSampleSeries)
                .toList();
        return new AomQueryMetricDataResponse(series);
    }

    private AomSampleSeries toSampleSeries(SampleDataValue sdk) {
        QuerySample sample = sdk.getSample();
        String namespace = sample == null ? null : sample.getNamespace();
        String metricName = sample == null ? null : sample.getMetricName();
        List<AomMetricDimension> dims;
        if (sample == null || sample.getDimensions() == null) {
            dims = Collections.emptyList();
        }
        else {
            dims = sample.getDimensions().stream()
                    .map(dim -> new AomMetricDimension(dim.getName(), dim.getValue()))
                    .toList();
        }
        List<MetricDataPoints> sdkDp =
                sdk.getDataPoints() == null ? Collections.emptyList() : sdk.getDataPoints();
        List<AomMetricDatapoint> datapoints = sdkDp.stream()
                .map(this::toDatapoint)
                .toList();
        return new AomSampleSeries(namespace, metricName, dims, datapoints);
    }

    private AomMetricDatapoint toDatapoint(MetricDataPoints sdk) {
        List<StatisticValue> sdkStats =
                sdk.getStatistics() == null ? Collections.emptyList() : sdk.getStatistics();
        List<AomStatisticValue> stats = sdkStats.stream()
                .map(stat -> new AomStatisticValue(stat.getStatistic(), stat.getValue()))
                .toList();
        return new AomMetricDatapoint(sdk.getTimestamp(), sdk.getUnit(), stats);
    }

    private ListLogItemsRequest toListLogItemsSdkRequest(AomQueryLogsRequest request) {
        QueryBodyParam body = new QueryBodyParam()
                .withCategory(request.category() == null ? null : request.category().getValue())
                .withStartTime(request.startTime())
                .withEndTime(request.endTime())
                .withPageSizeSize(String.valueOf(request.pageSize()))
                .withIsDesc(request.isDesc());
        if (request.keyWord() != null) {
            body.setKeyWord(request.keyWord());
        }
        return new ListLogItemsRequest()
                .withType("querylogs")
                .withBody(body);
    }
}
