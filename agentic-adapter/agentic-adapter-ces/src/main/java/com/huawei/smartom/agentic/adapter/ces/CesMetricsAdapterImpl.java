/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmAction;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmAdditionalInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmCondition;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmDataPoint;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmHistory;
import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmMetric;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricQuery;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricResult;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDatapoint;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPagination;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.model.BatchListMetricDataRequest;
import com.huaweicloud.sdk.ces.v1.model.BatchListMetricDataRequestBody;
import com.huaweicloud.sdk.ces.v1.model.BatchListMetricDataResponse;
import com.huaweicloud.sdk.ces.v1.model.BatchMetricData;
import com.huaweicloud.sdk.ces.v1.model.Datapoint;
import com.huaweicloud.sdk.ces.v1.model.DatapointForBatchMetric;
import com.huaweicloud.sdk.ces.v1.model.Filter;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsRequest;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsResponse;
import com.huaweicloud.sdk.ces.v1.model.MetaData;
import com.huaweicloud.sdk.ces.v1.model.MetricInfo;
import com.huaweicloud.sdk.ces.v1.model.MetricInfoList;
import com.huaweicloud.sdk.ces.v1.model.MetricsDimension;
import com.huaweicloud.sdk.ces.v1.model.ShowMetricDataRequest;
import com.huaweicloud.sdk.ces.v1.model.ShowMetricDataResponse;
import com.huaweicloud.sdk.ces.v2.model.AdditionalInfo;
import com.huaweicloud.sdk.ces.v2.model.AlarmHistoryItemV2;
import com.huaweicloud.sdk.ces.v2.model.AlarmHistoryItemV2AlarmActions;
import com.huaweicloud.sdk.ces.v2.model.AlarmHistoryItemV2Condition;
import com.huaweicloud.sdk.ces.v2.model.AlarmHistoryItemV2Metric;
import com.huaweicloud.sdk.ces.v2.model.AlarmHistoryItemV2MetricDimensions;
import com.huaweicloud.sdk.ces.v2.model.DataPointInfo;
import com.huaweicloud.sdk.ces.v2.model.ListAlarmHistoriesRequest;
import com.huaweicloud.sdk.ces.v2.model.ListAlarmHistoriesResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于华为云 Java SDK v3.1.177 的 {@link CesMetricsAdapter} 默认实现。
 *
 * <p>本类只承担对外 DTO 与 SDK 请求/响应类之间的映射。所有 SDK 异常都通过
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
    private static final String API_LIST_METRICS = "ces.listMetrics";
    private static final String API_SHOW_METRIC_DATA = "ces.showMetricData";
    private static final String API_LIST_ALARM_HISTORIES = "ces.listAlarmHistories";
    private static final String API_BATCH_LIST_METRIC_DATA = "ces.batchListMetricData";

    private final CesClient cesClient;
    private final com.huaweicloud.sdk.ces.v2.CesClient cesV2Client;
    private final HuaweiCloudInvocation invocation;

    /**
     * 构造 {@code CesMetricsAdapterImpl}，注入华为云 CES v1 / v2 SDK 客户端，以及统一的容错与异常映射调用帮助类。
     *
     * <p>v2 客户端目前仅用于 {@code listAlarmHistories}（T19 PR-1 切换）；其余接口仍走 v1。
     *
     * @param cesClient    已配置的华为云 CES v1 SDK 客户端
     * @param cesV2Client  已配置的华为云 CES v2 SDK 客户端
     * @param invocation   负责限流、重试以及 SDK 异常映射的调用帮助类
     */
    public CesMetricsAdapterImpl(
            CesClient cesClient,
            @Qualifier("cesV2Client") com.huaweicloud.sdk.ces.v2.CesClient cesV2Client,
            HuaweiCloudInvocation invocation) {
        this.cesClient = cesClient;
        this.cesV2Client = cesV2Client;
        this.invocation = invocation;
    }

    @Override
    public CesListMetricsResponse listMetrics(CesListMetricsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("ces.listMetrics start, namespace={}, metricName={}, dimName={}, limit={}",
                request.namespace(), request.metricName(), request.dimName(), request.limit());

        ListMetricsRequest sdkRequest = toListMetricsSdkRequest(request);
        ListMetricsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_METRICS,
                () -> cesClient.listMetrics(sdkRequest));

        return toListMetricsResponseDto(sdkResponse);
    }

    @Override
    public CesQueryMetricDataResponse queryMetricData(CesQueryMetricDataRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("ces.showMetricData start, namespace={}, metricName={}, filter={}, period={}, from={}, to={}",
                request.namespace(), request.metricName(), request.filter(),
                request.period(), request.from(), request.to());

        ShowMetricDataRequest sdkRequest = toShowMetricDataSdkRequest(request);
        ShowMetricDataResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_SHOW_METRIC_DATA,
                () -> cesClient.showMetricData(sdkRequest));

        return toQueryMetricDataResponseDto(sdkResponse, request.namespace());
    }

    @Override
    public CesBatchQueryMetricDataResponse batchQueryMetricData(CesBatchQueryMetricDataRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int metricCount = request.metrics() == null ? 0 : request.metrics().size();
        LOG.info("ces.batchListMetricData start, metricCount={}, filter={}, period={}, from={}, to={}",
                metricCount, request.filter(), request.period(), request.from(), request.to());

        BatchListMetricDataRequest sdkRequest = toBatchListMetricDataSdkRequest(request);
        BatchListMetricDataResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_BATCH_LIST_METRIC_DATA,
                () -> cesClient.batchListMetricData(sdkRequest));

        return toBatchQueryMetricDataResponseDto(sdkResponse);
    }

    @Override
    public CesListAlarmsResponse listAlarms(CesListAlarmsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("ces.listAlarmHistories start, namespace={}, alarmId={}, status={}, level={}, limit={}",
                request.namespace(), request.alarmId(), request.alarmStatus(),
                request.alarmLevel(), request.limit());

        ListAlarmHistoriesRequest sdkRequest = toListAlarmHistoriesSdkRequest(request);
        ListAlarmHistoriesResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_ALARM_HISTORIES,
                () -> cesV2Client.listAlarmHistories(sdkRequest));

        return toListAlarmsResponseDto(sdkResponse);
    }

    private ListMetricsRequest toListMetricsSdkRequest(CesListMetricsRequest request) {
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
        sdk.setOrder(toOrderEnum(request.order()));
        return sdk;
    }

    private ListMetricsRequest.OrderEnum toOrderEnum(String order) {
        if ("asc".equals(order)) {
            return ListMetricsRequest.OrderEnum.ASC;
        }
        return ListMetricsRequest.OrderEnum.DESC;
    }

    private CesListMetricsResponse toListMetricsResponseDto(ListMetricsResponse sdkResp) {
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

    private ShowMetricDataRequest toShowMetricDataSdkRequest(CesQueryMetricDataRequest request) {
        ShowMetricDataRequest sdk = new ShowMetricDataRequest()
                .withNamespace(request.namespace())
                .withMetricName(request.metricName())
                .withFilter(ShowMetricDataRequest.FilterEnum.fromValue(request.filter().getValue()))
                .withPeriod(ShowMetricDataRequest.PeriodEnum.fromValue(request.period().getSeconds()))
                .withFrom(request.from())
                .withTo(request.to());

        List<CesMetricDimension> dims = request.dimensions() == null
                ? Collections.emptyList()
                : request.dimensions();
        for (int idx = 0; idx < dims.size(); idx++) {
            CesMetricDimension dim = dims.get(idx);
            String value = dim.name() + "," + dim.value();
            switch (idx) {
                case 0 -> sdk.setDim0(value);
                case 1 -> sdk.setDim1(value);
                case 2 -> sdk.setDim2(value);
                case 3 -> sdk.setDim3(value);
                default -> {
                    // CES API supports at most 4 dimensions; extras are validated out upstream.
                }
            }
        }
        return sdk;
    }

    private CesQueryMetricDataResponse toQueryMetricDataResponseDto(
            ShowMetricDataResponse sdkResp, String resolvedNamespace) {
        List<Datapoint> sdkDatapoints =
                sdkResp.getDatapoints() == null ? Collections.emptyList() : sdkResp.getDatapoints();
        List<CesDatapoint> datapoints = sdkDatapoints.stream()
                .map(this::toDatapoint)
                .toList();
        return new CesQueryMetricDataResponse(sdkResp.getMetricName(), datapoints, resolvedNamespace);
    }

    private CesDatapoint toDatapoint(Datapoint sdk) {
        return new CesDatapoint(
                sdk.getTimestamp(),
                sdk.getUnit(),
                sdk.getMax(),
                sdk.getMin(),
                sdk.getAverage(),
                sdk.getSum(),
                sdk.getVariance());
    }

    private BatchListMetricDataRequest toBatchListMetricDataSdkRequest(CesBatchQueryMetricDataRequest request) {
        List<CesBatchMetricQuery> metrics = request.metrics() == null
                ? Collections.emptyList()
                : request.metrics();
        List<MetricInfo> sdkMetrics = metrics.stream()
                .map(this::toSdkMetricInfo)
                .toList();

        BatchListMetricDataRequestBody body = new BatchListMetricDataRequestBody()
                .withMetrics(sdkMetrics)
                .withPeriod(BatchListMetricDataRequestBody.PeriodEnum.fromValue(
                        String.valueOf(request.period().getSeconds())))
                .withFilter(Filter.fromValue(request.filter().getValue()))
                .withFrom(request.from())
                .withTo(request.to());
        return new BatchListMetricDataRequest().withBody(body);
    }

    private MetricInfo toSdkMetricInfo(CesBatchMetricQuery query) {
        List<CesMetricDimension> dims = query.dimensions() == null
                ? Collections.emptyList()
                : query.dimensions();
        List<MetricsDimension> sdkDims = dims.stream()
                .map(dim -> new MetricsDimension().withName(dim.name()).withValue(dim.value()))
                .toList();
        return new MetricInfo()
                .withNamespace(query.namespace())
                .withMetricName(query.metricName())
                .withDimensions(sdkDims);
    }

    private CesBatchQueryMetricDataResponse toBatchQueryMetricDataResponseDto(BatchListMetricDataResponse sdkResp) {
        List<BatchMetricData> sdkMetrics =
                sdkResp.getMetrics() == null ? Collections.emptyList() : sdkResp.getMetrics();
        List<CesBatchMetricResult> results = sdkMetrics.stream()
                .map(this::toBatchMetricResult)
                .toList();
        return new CesBatchQueryMetricDataResponse(results);
    }

    private CesBatchMetricResult toBatchMetricResult(BatchMetricData sdk) {
        List<MetricsDimension> sdkDims =
                sdk.getDimensions() == null ? Collections.emptyList() : sdk.getDimensions();
        List<CesMetricDimension> dims = sdkDims.stream()
                .map(dim -> new CesMetricDimension(dim.getName(), dim.getValue()))
                .toList();
        List<DatapointForBatchMetric> sdkPoints =
                sdk.getDatapoints() == null ? Collections.emptyList() : sdk.getDatapoints();
        List<CesDatapoint> points = sdkPoints.stream()
                .map(this::toBatchDatapoint)
                .toList();
        // resolved_namespace 取上游回显的 namespace——批量请求经 service 层 RDS fallback 后,
        // 回显值即实际取数的命名空间，非 RDS 场景天然等于入参值。
        return new CesBatchMetricResult(
                sdk.getNamespace(),
                sdk.getMetricName(),
                dims,
                sdk.getUnit(),
                points,
                sdk.getNamespace());
    }

    private CesDatapoint toBatchDatapoint(DatapointForBatchMetric sdk) {
        return new CesDatapoint(
                sdk.getTimestamp(),
                null,
                sdk.getMax(),
                sdk.getMin(),
                sdk.getAverage(),
                sdk.getSum(),
                sdk.getVariance());
    }

    private ListAlarmHistoriesRequest toListAlarmHistoriesSdkRequest(CesListAlarmsRequest request) {
        // CES v2 接口走 HTTP GET，所有参数都在 query 上；与 v1 POST 形态不同。
        // 注意：v2 不再支持 group_id 过滤，CesListAlarmsRequest.groupId 若被设置则 service 层校验
        // 已经拦下；这里再次跳过该字段以双保险。
        ListAlarmHistoriesRequest sdk = new ListAlarmHistoriesRequest()
                .withOffset(request.start())
                .withLimit(request.limit());
        if (request.alarmId() != null) {
            sdk.setAlarmId(Collections.singletonList(request.alarmId()));
        }
        if (request.alarmName() != null) {
            sdk.setName(request.alarmName());
        }
        if (request.alarmStatus() != null) {
            sdk.setStatus(Collections.singletonList(
                    ListAlarmHistoriesRequest.StatusEnum.fromValue(request.alarmStatus())));
        }
        if (request.alarmLevel() != null) {
            sdk.setLevel(request.alarmLevel());
        }
        if (request.namespace() != null) {
            sdk.setNamespace(request.namespace());
        }
        if (request.from() != null) {
            sdk.setFrom(request.from());
        }
        if (request.to() != null) {
            sdk.setTo(request.to());
        }
        return sdk;
    }

    private CesListAlarmsResponse toListAlarmsResponseDto(ListAlarmHistoriesResponse sdkResp) {
        List<AlarmHistoryItemV2> sdkAlarms =
                sdkResp.getAlarmHistories() == null ? Collections.emptyList() : sdkResp.getAlarmHistories();
        List<CesAlarmHistory> alarms = sdkAlarms.stream()
                .map(this::toAlarmHistory)
                .toList();
        return new CesListAlarmsResponse(alarms, sdkResp.getCount());
    }

    private CesAlarmHistory toAlarmHistory(AlarmHistoryItemV2 sdk) {
        CesAlarmMetric metric = toAlarmMetric(sdk.getMetric());
        String namespace = metric == null ? null : metric.namespace();
        String metricName = metric == null ? null : metric.metricName();

        Long triggerTime = toMillis(sdk.getFirstAlarmTime());
        Long updateTime = toMillis(sdk.getLastAlarmTime());

        return new CesAlarmHistory(
                sdk.getAlarmId(),
                sdk.getName(),
                null,  // v2 没有 alarm_description 字段，保留 DTO 字段位但取 null
                sdk.getLevel() == null ? null : sdk.getLevel().getValue(),
                sdk.getType() == null ? null : sdk.getType().getValue(),
                sdk.getStatus() == null ? null : sdk.getStatus().getValue(),
                namespace,
                metricName,
                triggerTime,
                updateTime,
                sdk.getRecordId(),
                sdk.getActionEnabled(),
                sdk.getBeginTime(),
                sdk.getEndTime(),
                sdk.getFirstAlarmTime(),
                sdk.getLastAlarmTime(),
                sdk.getAlarmRecoveryTime(),
                metric,
                toAlarmCondition(sdk.getCondition()),
                toAlarmAdditionalInfo(sdk.getAdditionalInfo()),
                toAlarmActions(sdk.getAlarmActions()),
                toAlarmActions(sdk.getOkActions()),
                toAlarmDataPoints(sdk.getDataPoints()));
    }

    private CesAlarmMetric toAlarmMetric(AlarmHistoryItemV2Metric sdk) {
        if (sdk == null) {
            return null;
        }
        List<CesMetricDimension> dims = sdk.getDimensions() == null
                ? null
                : sdk.getDimensions().stream()
                        .map(this::toAlarmMetricDimension)
                        .toList();
        return new CesAlarmMetric(sdk.getNamespace(), sdk.getMetricName(), dims);
    }

    private CesMetricDimension toAlarmMetricDimension(AlarmHistoryItemV2MetricDimensions sdk) {
        return new CesMetricDimension(sdk.getName(), sdk.getValue());
    }

    private CesAlarmCondition toAlarmCondition(AlarmHistoryItemV2Condition sdk) {
        if (sdk == null) {
            return null;
        }
        Integer period = sdk.getPeriod() == null ? null : sdk.getPeriod().getValue();
        Integer suppressDuration = sdk.getSuppressDuration() == null
                ? null
                : sdk.getSuppressDuration().getValue();
        return new CesAlarmCondition(
                period,
                sdk.getFilter(),
                sdk.getComparisonOperator(),
                sdk.getValue(),
                sdk.getUnit(),
                sdk.getCount(),
                suppressDuration);
    }

    private CesAlarmAdditionalInfo toAlarmAdditionalInfo(AdditionalInfo sdk) {
        if (sdk == null) {
            return null;
        }
        return new CesAlarmAdditionalInfo(sdk.getResourceId(), sdk.getResourceName(), sdk.getEventId());
    }

    private List<CesAlarmAction> toAlarmActions(List<AlarmHistoryItemV2AlarmActions> sdkActions) {
        if (sdkActions == null) {
            return null;
        }
        return sdkActions.stream()
                .map(action -> new CesAlarmAction(
                        action.getType() == null ? null : action.getType().getValue(),
                        action.getNotificationList()))
                .toList();
    }

    private List<CesAlarmDataPoint> toAlarmDataPoints(List<DataPointInfo> sdkPoints) {
        if (sdkPoints == null) {
            return null;
        }
        return sdkPoints.stream()
                .map(point -> new CesAlarmDataPoint(point.getTime(), point.getValue()))
                .toList();
    }

    private Long toMillis(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toEpochMilli();
    }
}
