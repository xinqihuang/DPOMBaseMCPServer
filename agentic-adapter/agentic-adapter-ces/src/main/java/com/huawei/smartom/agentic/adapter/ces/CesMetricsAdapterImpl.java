/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesAlarmHistory;
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
import com.huaweicloud.sdk.ces.v1.model.AlarmHistoryInfoResp;
import com.huaweicloud.sdk.ces.v1.model.Datapoint;
import com.huaweicloud.sdk.ces.v1.model.ListAlarmHistoriesRequest;
import com.huaweicloud.sdk.ces.v1.model.ListAlarmHistoriesResponse;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsRequest;
import com.huaweicloud.sdk.ces.v1.model.ListMetricsResponse;
import com.huaweicloud.sdk.ces.v1.model.MetaData;
import com.huaweicloud.sdk.ces.v1.model.MetaDataForAlarmHistoryResp;
import com.huaweicloud.sdk.ces.v1.model.MetricInfoList;
import com.huaweicloud.sdk.ces.v1.model.MetricInfoResp;
import com.huaweicloud.sdk.ces.v1.model.MetricsDimension;
import com.huaweicloud.sdk.ces.v1.model.ShowMetricDataRequest;
import com.huaweicloud.sdk.ces.v1.model.ShowMetricDataResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

        return toQueryMetricDataResponseDto(sdkResponse);
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
                () -> cesClient.listAlarmHistories(sdkRequest));

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
                .withFilter(ShowMetricDataRequest.FilterEnum.fromValue(request.filter()))
                .withPeriod(ShowMetricDataRequest.PeriodEnum.fromValue(request.period()))
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

    private CesQueryMetricDataResponse toQueryMetricDataResponseDto(ShowMetricDataResponse sdkResp) {
        List<Datapoint> sdkDatapoints =
                sdkResp.getDatapoints() == null ? Collections.emptyList() : sdkResp.getDatapoints();
        List<CesDatapoint> datapoints = sdkDatapoints.stream()
                .map(this::toDatapoint)
                .toList();
        return new CesQueryMetricDataResponse(sdkResp.getMetricName(), datapoints);
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

    private ListAlarmHistoriesRequest toListAlarmHistoriesSdkRequest(CesListAlarmsRequest request) {
        ListAlarmHistoriesRequest sdk = new ListAlarmHistoriesRequest()
                .withLimit(String.valueOf(request.limit()))
                .withStart(String.valueOf(request.start()));
        if (request.groupId() != null) {
            sdk.setGroupId(request.groupId());
        }
        if (request.alarmId() != null) {
            sdk.setAlarmId(request.alarmId());
        }
        if (request.alarmName() != null) {
            sdk.setAlarmName(request.alarmName());
        }
        if (request.alarmStatus() != null) {
            sdk.setAlarmStatus(ListAlarmHistoriesRequest.AlarmStatusEnum.fromValue(request.alarmStatus()));
        }
        if (request.alarmLevel() != null) {
            sdk.setAlarmLevel(ListAlarmHistoriesRequest.AlarmLevelEnum.fromValue(request.alarmLevel()));
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
        List<AlarmHistoryInfoResp> sdkAlarms =
                sdkResp.getAlarmHistories() == null ? Collections.emptyList() : sdkResp.getAlarmHistories();
        List<CesAlarmHistory> alarms = sdkAlarms.stream()
                .map(this::toAlarmHistory)
                .toList();
        MetaDataForAlarmHistoryResp meta = sdkResp.getMetaData();
        Integer total = meta == null ? null : meta.getTotal();
        return new CesListAlarmsResponse(alarms, total);
    }

    private CesAlarmHistory toAlarmHistory(AlarmHistoryInfoResp sdk) {
        MetricInfoResp metric = sdk.getMetric();
        String namespace = metric == null ? null : metric.getNamespace();
        String metricName = metric == null ? null : metric.getMetricName();
        return new CesAlarmHistory(
                sdk.getAlarmId(),
                sdk.getAlarmName(),
                sdk.getAlarmDescription(),
                sdk.getAlarmLevel(),
                sdk.getAlarmType(),
                sdk.getAlarmStatus(),
                namespace,
                metricName,
                sdk.getTriggerTime(),
                sdk.getUpdateTime());
    }
}
