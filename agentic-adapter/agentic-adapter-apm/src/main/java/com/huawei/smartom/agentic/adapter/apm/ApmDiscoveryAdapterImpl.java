/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmCollectorCategoryInfo;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemEntity;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendFieldItem;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmViewBase;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmViewRow;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.CollectorCategoryInfo;
import com.huaweicloud.sdk.apm.v1.model.FieldItem;
import com.huaweicloud.sdk.apm.v1.model.MonitorItemEntity;
import com.huaweicloud.sdk.apm.v1.model.ShowEnvMonitorItemsRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowEnvMonitorItemsResponse;
import com.huaweicloud.sdk.apm.v1.model.ShowMonitorItemViewConfigRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowMonitorItemViewConfigResponse;
import com.huaweicloud.sdk.apm.v1.model.ViewBase;
import com.huaweicloud.sdk.apm.v1.model.ViewRow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于华为云 Java SDK v3.1.177 的 {@link ApmDiscoveryAdapter} 实现。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmDiscoveryAdapterImpl implements ApmDiscoveryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ApmDiscoveryAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "apm-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_SHOW_ENV_MONITOR_ITEMS = "apm.showEnvMonitorItems";
    private static final String API_SHOW_MONITOR_ITEM_VIEW_CONFIG = "apm.showMonitorItemViewConfig";

    private final ApmClient apmClient;
    private final HuaweiCloudInvocation invocation;
    private final HuaweiCloudProperties properties;

    /**
     * 构造 {@code ApmDiscoveryAdapterImpl}。
     *
     * @param apmClient  已配置好的华为云 APM SDK 客户端
     * @param invocation 限流 / 重试 / 异常映射调用助手
     * @param properties 华为云配置，用于读取 APM 默认 business id
     */
    public ApmDiscoveryAdapterImpl(
            ApmClient apmClient,
            HuaweiCloudInvocation invocation,
            HuaweiCloudProperties properties) {
        this.apmClient = apmClient;
        this.invocation = invocation;
        this.properties = properties;
    }

    @Override
    public ApmEnvMonitorItemsResponse showEnvMonitorItems(Long envId, Long businessId) {
        Long effectiveBusinessId = businessId == null ? properties.getApmBusinessId() : businessId;
        LOG.info("apm.showEnvMonitorItems start, envId={}, businessId={}", envId, effectiveBusinessId);

        ShowEnvMonitorItemsRequest sdkReq = new ShowEnvMonitorItemsRequest().withEnvId(envId);
        if (effectiveBusinessId != null) {
            sdkReq.setXBusinessId(effectiveBusinessId);
        }

        ShowEnvMonitorItemsResponse sdkResp = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_SHOW_ENV_MONITOR_ITEMS,
                () -> apmClient.showEnvMonitorItems(sdkReq));

        return toEnvMonitorItemsDto(sdkResp);
    }

    @Override
    public ApmMonitorItemViewConfigResponse showMonitorItemViewConfig(
            Long collectorId, Long envId, Long businessId) {
        Long effectiveBusinessId = businessId == null ? properties.getApmBusinessId() : businessId;
        LOG.info("apm.showMonitorItemViewConfig start, envId={}, collectorId={}, businessId={}",
                envId, collectorId, effectiveBusinessId);

        ShowMonitorItemViewConfigRequest sdkReq = new ShowMonitorItemViewConfigRequest()
                .withEnvId(envId)
                .withCollectorId(collectorId);
        if (effectiveBusinessId != null) {
            sdkReq.setXBusinessId(effectiveBusinessId);
        }

        ShowMonitorItemViewConfigResponse sdkResp = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_SHOW_MONITOR_ITEM_VIEW_CONFIG,
                () -> apmClient.showMonitorItemViewConfig(sdkReq));

        return toViewConfigDto(sdkResp);
    }

    private ApmEnvMonitorItemsResponse toEnvMonitorItemsDto(ShowEnvMonitorItemsResponse sdkResp) {
        List<CollectorCategoryInfo> sdkCategories = sdkResp.getCategoryInfoList() == null
                ? Collections.emptyList()
                : sdkResp.getCategoryInfoList();
        List<ApmCollectorCategoryInfo> categories = sdkCategories.stream()
                .map(this::toApmCategory)
                .toList();
        List<MonitorItemEntity> sdkItems = sdkResp.getMonitorItemInfoList() == null
                ? Collections.emptyList()
                : sdkResp.getMonitorItemInfoList();
        List<ApmMonitorItemEntity> items = sdkItems.stream()
                .map(this::toApmMonitorItem)
                .toList();
        return new ApmEnvMonitorItemsResponse(categories, items);
    }

    private ApmCollectorCategoryInfo toApmCategory(CollectorCategoryInfo sdk) {
        return new ApmCollectorCategoryInfo(
                sdk.getCategoryId(),
                sdk.getCategoryName(),
                sdk.getDisplayName(),
                sdk.getSequence());
    }

    private ApmMonitorItemEntity toApmMonitorItem(MonitorItemEntity sdk) {
        return new ApmMonitorItemEntity(
                sdk.getCategoryId(),
                sdk.getCollectorName(),
                sdk.getDisplayName(),
                sdk.getShowInTotal(),
                sdk.getMonitorItemId(),
                sdk.getDisabled(),
                sdk.getCollectorId(),
                sdk.getSequence(),
                sdk.getCollectInterval());
    }

    private ApmMonitorItemViewConfigResponse toViewConfigDto(ShowMonitorItemViewConfigResponse sdkResp) {
        List<ViewRow> sdkRows = sdkResp.getViewRowList() == null
                ? Collections.emptyList()
                : sdkResp.getViewRowList();
        List<ApmViewRow> rows = sdkRows.stream()
                .map(this::toApmViewRow)
                .toList();
        return new ApmMonitorItemViewConfigResponse(
                sdkResp.getTitle(),
                sdkResp.getCollectorName(),
                rows,
                sdkResp.getStyle());
    }

    private ApmViewRow toApmViewRow(ViewRow sdk) {
        List<ViewBase> sdkViews = sdk.getViewList() == null
                ? Collections.emptyList()
                : sdk.getViewList();
        List<ApmViewBase> views = sdkViews.stream()
                .map(this::toApmViewBase)
                .toList();
        return new ApmViewRow(views, sdk.getTitle());
    }

    private ApmViewBase toApmViewBase(ViewBase sdk) {
        List<ApmTrendFieldItem> fieldItems = sdk.getFieldItemList() == null
                ? null
                : sdk.getFieldItemList().stream().map(this::toApmFieldItem).toList();
        return new ApmViewBase(
                sdk.getCollectorName(),
                sdk.getMetricSet(),
                sdk.getTitle(),
                sdk.getTableDirection() == null ? null : sdk.getTableDirection().getValue(),
                sdk.getGroupBy(),
                sdk.getFilter(),
                fieldItems,
                sdk.getSpan(),
                sdk.getSpanField(),
                sdk.getOrderBy(),
                sdk.getLatest(),
                sdk.getViewType() == null ? null : sdk.getViewType().getValue());
    }

    private ApmTrendFieldItem toApmFieldItem(FieldItem sdk) {
        return new ApmTrendFieldItem(
                sdk.getFunction(),
                sdk.getAs(),
                sdk.getDefaultValue(),
                sdk.getTrace(),
                sdk.getPrecision(),
                sdk.getUnit(),
                sdk.getVisible());
    }
}
