/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendFieldItem;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendLine;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendPoint;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.FieldItem;
import com.huaweicloud.sdk.apm.v1.model.FrontLine;
import com.huaweicloud.sdk.apm.v1.model.FrontPoint;
import com.huaweicloud.sdk.apm.v1.model.ShowTrendRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowTrendResponse;
import com.huaweicloud.sdk.apm.v1.model.TrendParam;
import com.huaweicloud.sdk.apm.v1.model.TrendView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于华为云 Java SDK v3.1.177 的 {@link ApmTrendAdapter} 实现。
 *
 * <p>将 DTO 入参装配为 SDK {@code ShowTrendRequest}（含嵌套 {@code TrendParam} /
 * {@code TrendView} / {@code FieldItem}），调用上游后将 {@code ShowTrendResponse} 无损
 * 投影为 {@link ApmTrendResponse}（含 {@code FrontLine} × {@code FrontPoint}）。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmTrendAdapterImpl implements ApmTrendAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ApmTrendAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "apm-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_SHOW_TREND = "apm.showTrend";

    private final ApmClient apmClient;
    private final HuaweiCloudInvocation invocation;
    private final HuaweiCloudProperties properties;

    /**
     * 构造 {@code ApmTrendAdapterImpl}。
     *
     * @param apmClient  已配置好的华为云 APM SDK 客户端
     * @param invocation 限流 / 重试 / 异常映射调用助手
     * @param properties 华为云配置，用于读取 APM 默认 business id
     */
    public ApmTrendAdapterImpl(
            ApmClient apmClient,
            HuaweiCloudInvocation invocation,
            HuaweiCloudProperties properties) {
        this.apmClient = apmClient;
        this.invocation = invocation;
        this.properties = properties;
    }

    @Override
    public ApmTrendResponse showTrend(ApmTrendRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ApmTrendViewConfig view = Objects.requireNonNull(request.viewConfig(),
                "view_config must not be null");
        Long businessId = request.businessId() == null
                ? properties.getApmBusinessId()
                : request.businessId();
        LOG.info("apm.showTrend start, businessId={}, monitorItemId={}, viewType={}, metricSet={}, "
                        + "startTime={}, endTime={}",
                businessId, request.monitorItemId(), view.viewType(), view.metricSet(),
                request.startTime(), request.endTime());

        TrendParam body = new TrendParam()
                .withViewConfig(toSdkView(view))
                .withInstanceId(request.instanceId())
                .withMonitorItemId(request.monitorItemId())
                .withEnvId(request.envId())
                .withStartTime(request.startTime())
                .withEndTime(request.endTime());

        ShowTrendRequest sdkRequest = new ShowTrendRequest().withBody(body);
        if (businessId != null) {
            sdkRequest.setXBusinessId(businessId);
        }

        ShowTrendResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_SHOW_TREND,
                () -> apmClient.showTrend(sdkRequest));

        return toResponseDto(sdkResponse);
    }

    private TrendView toSdkView(ApmTrendViewConfig dto) {
        TrendView view = new TrendView()
                .withViewType(dto.viewType() == null ? null : TrendView.ViewTypeEnum.fromValue(dto.viewType()))
                .withCollectorName(dto.collectorName())
                .withMetricSet(dto.metricSet())
                .withTitle(dto.title())
                .withTableDirection(dto.tableDirection() == null
                        ? null : TrendView.TableDirectionEnum.fromValue(dto.tableDirection()))
                .withGroupBy(dto.groupBy())
                .withFilter(dto.filter())
                .withSpan(dto.span())
                .withSpanField(dto.spanField())
                .withOrderBy(dto.orderBy())
                .withLatest(dto.latest());
        if (dto.fieldItemList() != null) {
            view.setFieldItemList(dto.fieldItemList().stream().map(this::toSdkFieldItem).toList());
        }
        return view;
    }

    private FieldItem toSdkFieldItem(ApmTrendFieldItem dto) {
        return new FieldItem()
                .withFunction(dto.function())
                .withAs(dto.as())
                .withDefaultValue(dto.defaultValue())
                .withTrace(dto.trace())
                .withPrecision(dto.precision())
                .withUnit(dto.unit())
                .withVisible(dto.visible());
    }

    private ApmTrendResponse toResponseDto(ShowTrendResponse sdkResp) {
        List<FrontLine> sdkLines = sdkResp.getLineList() == null
                ? Collections.emptyList()
                : sdkResp.getLineList();
        List<ApmTrendLine> lines = sdkLines.stream()
                .map(this::toApmTrendLine)
                .toList();
        return new ApmTrendResponse(lines, sdkResp.getLatestDataTime());
    }

    private ApmTrendLine toApmTrendLine(FrontLine sdk) {
        List<FrontPoint> sdkPoints = sdk.getPointList() == null
                ? Collections.emptyList()
                : sdk.getPointList();
        List<ApmTrendPoint> points = sdkPoints.stream()
                .map(this::toApmTrendPoint)
                .toList();
        return new ApmTrendLine(
                points,
                sdk.getTitle(),
                sdk.getUnit(),
                sdk.getPrecision(),
                sdk.getDataType(),
                sdk.getVisible());
    }

    private ApmTrendPoint toApmTrendPoint(FrontPoint sdk) {
        return new ApmTrendPoint(sdk.getTime(), sdk.getValue());
    }
}
