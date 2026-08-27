/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMask;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskHierarchicalValue;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskMetricExtraInfo;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskPolicy;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNotificationMaskResourceCategory;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskProductMetric;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.ces.v2.CesClient;
import com.huaweicloud.sdk.ces.v2.model.HierarchicalValue;
import com.huaweicloud.sdk.ces.v2.model.ListNotificationMaskRequestBody;
import com.huaweicloud.sdk.ces.v2.model.ListNotificationMaskRespNotificationMasks;
import com.huaweicloud.sdk.ces.v2.model.ListNotificationMasksRequest;
import com.huaweicloud.sdk.ces.v2.model.ListNotificationMasksResponse;
import com.huaweicloud.sdk.ces.v2.model.ListRelationType;
import com.huaweicloud.sdk.ces.v2.model.MetricExtraInfo;
import com.huaweicloud.sdk.ces.v2.model.PoliciesInListResp;
import com.huaweicloud.sdk.ces.v2.model.ResourceCategory;
import com.huaweicloud.sdk.ces.v2.model.ResourceDimension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 基于华为云 Java SDK V2 ({@code com.huaweicloud.sdk.ces.v2.CesClient}) 的
 * {@link CesNotificationMaskAdapter} 实现。
 *
 * <p>所有 SDK 异常通过 {@link HuaweiCloudInvocation} 统一映射为 {@code SmartomException}。
 * 限流命名为 {@code ces-readonly}，重试沿用 {@code huaweicloud-retryable} 实例。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CesNotificationMaskAdapterImpl implements CesNotificationMaskAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CesNotificationMaskAdapterImpl.class);

    private static final String RATE_LIMITER_READ = "ces-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_LIST = "ces.listNotificationMasks";

    private final CesClient cesV2Client;
    private final HuaweiCloudInvocation invocation;

    /**
     * 构造 {@code CesNotificationMaskAdapterImpl}。
     *
     * @param cesV2Client CES V2 SDK 客户端（由 {@code CesV2ClientConfig} 提供）
     * @param invocation  限流 / 重试 / 异常映射调用助手
     */
    public CesNotificationMaskAdapterImpl(CesClient cesV2Client, HuaweiCloudInvocation invocation) {
        this.cesV2Client = cesV2Client;
        this.invocation = invocation;
    }

    @Override
    public CesListNotificationMasksResponse listNotificationMasks(CesListNotificationMasksRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("ces.listNotificationMasks start, offset={}, limit={}, maskName={}, maskStatus={}",
                request.offset(), request.limit(), request.maskName(), request.maskStatus());

        ListNotificationMasksRequest sdkRequest = toListSdkRequest(request);
        ListNotificationMasksResponse sdkResponse = invocation.execute(
                RATE_LIMITER_READ, RETRY_NAME, API_LIST,
                () -> cesV2Client.listNotificationMasks(sdkRequest));

        List<ListNotificationMaskRespNotificationMasks> sdkMasks =
                sdkResponse.getNotificationMasks() == null
                        ? Collections.emptyList()
                        : sdkResponse.getNotificationMasks();
        List<CesNotificationMask> masks = sdkMasks.stream()
                .map(this::toNotificationMask)
                .toList();
        return new CesListNotificationMasksResponse(masks, sdkResponse.getCount());
    }

    private ListNotificationMasksRequest toListSdkRequest(CesListNotificationMasksRequest request) {
        ListNotificationMasksRequest sdk = new ListNotificationMasksRequest()
                .withOffset(request.offset())
                .withLimit(request.limit());
        if (request.sortKey() != null) {
            sdk.setSortKey(ListNotificationMasksRequest.SortKeyEnum.fromValue(request.sortKey()));
        }
        if (request.sortDir() != null) {
            sdk.setSortDir(ListNotificationMasksRequest.SortDirEnum.fromValue(request.sortDir()));
        }
        ListNotificationMaskRequestBody body = toListSdkRequestBody(request);
        if (body != null) {
            sdk.setBody(body);
        }
        return sdk;
    }

    private ListNotificationMaskRequestBody toListSdkRequestBody(CesListNotificationMasksRequest req) {
        ListNotificationMaskRequestBody body = new ListNotificationMaskRequestBody();
        boolean has = false;
        has |= applyIfPresent(req.relationType(), val -> body.setRelationType(ListRelationType.fromValue(val)));
        has |= applyIfPresent(nullIfEmpty(req.relationIds()), body::setRelationIds);
        has |= applyIfPresent(req.metricName(), body::setMetricName);
        has |= applyIfPresent(req.resourceLevel(),
                val -> body.setResourceLevel(ListNotificationMaskRequestBody.ResourceLevelEnum.fromValue(val)));
        has |= applyIfPresent(req.maskId(), body::setMaskId);
        has |= applyIfPresent(req.maskName(), body::setMaskName);
        has |= applyIfPresent(req.maskStatus(),
                val -> body.setMaskStatus(ListNotificationMaskRequestBody.MaskStatusEnum.fromValue(val)));
        has |= applyIfPresent(req.resourceId(), body::setResourceId);
        has |= applyIfPresent(req.namespace(), body::setNamespace);
        List<ResourceDimension> dims = req.dimensions() == null ? null
                : req.dimensions().stream()
                        .map(dim -> new ResourceDimension().withName(dim.name()).withValue(dim.value()))
                        .toList();
        has |= applyIfPresent(nullIfEmpty(dims), body::setDimensions);
        return has ? body : null;
    }

    private static <T> boolean applyIfPresent(T value, Consumer<T> setter) {
        if (value == null) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private static <T> List<T> nullIfEmpty(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }

    private CesNotificationMask toNotificationMask(ListNotificationMaskRespNotificationMasks sdk) {
        List<NotificationMaskProductMetric> productMetrics = sdk.getProductMetrics() == null ? null
                : sdk.getProductMetrics().stream()
                        .map(item -> new NotificationMaskProductMetric(
                                item.getDimensionName(), item.getMetricName()))
                        .toList();
        List<CesNotificationMaskResourceCategory> resources = sdk.getResources() == null ? null
                : sdk.getResources().stream()
                        .map(this::toResourceCategory)
                        .toList();
        List<CesNotificationMaskPolicy> policies = sdk.getPolicies() == null ? null
                : sdk.getPolicies().stream()
                        .map(this::toPolicy)
                        .toList();
        return new CesNotificationMask(
                sdk.getNotificationMaskId(),
                sdk.getMaskName(),
                sdk.getRelationType() == null ? null : sdk.getRelationType().getValue(),
                sdk.getRelationId(),
                sdk.getResourceType() == null ? null : sdk.getResourceType().getValue(),
                sdk.getMetricNames(),
                productMetrics,
                sdk.getResourceLevel() == null ? null : sdk.getResourceLevel().getValue(),
                sdk.getProductName(),
                resources,
                sdk.getMaskStatus() == null ? null : sdk.getMaskStatus().getValue(),
                sdk.getMaskType() == null ? null : sdk.getMaskType().getValue(),
                sdk.getCreateTime(),
                sdk.getUpdateTime(),
                sdk.getStartDate() == null ? null : sdk.getStartDate().toString(),
                sdk.getStartTime(),
                sdk.getEndDate() == null ? null : sdk.getEndDate().toString(),
                sdk.getEndTime(),
                sdk.getEffectiveTimezone(),
                policies);
    }

    private CesNotificationMaskResourceCategory toResourceCategory(ResourceCategory sdk) {
        return new CesNotificationMaskResourceCategory(sdk.getNamespace(), sdk.getDimensionNames());
    }

    private CesNotificationMaskPolicy toPolicy(PoliciesInListResp sdk) {
        return new CesNotificationMaskPolicy(
                sdk.getAlarmPolicyId(),
                sdk.getMetricName(),
                toMetricExtraInfo(sdk.getExtraInfo()),
                sdk.getPeriod() == null ? null : sdk.getPeriod().getValue(),
                sdk.getFilter(),
                sdk.getComparisonOperator(),
                sdk.getValue(),
                toHierarchicalValue(sdk.getHierarchicalValue()),
                sdk.getUnit(),
                sdk.getCount(),
                sdk.getType(),
                sdk.getSuppressDuration() == null ? null : sdk.getSuppressDuration().getValue(),
                sdk.getAlarmLevel(),
                sdk.getSelectedUnit());
    }

    private CesNotificationMaskMetricExtraInfo toMetricExtraInfo(MetricExtraInfo sdk) {
        if (sdk == null) {
            return null;
        }
        return new CesNotificationMaskMetricExtraInfo(
                sdk.getOriginMetricName(),
                sdk.getMetricPrefix(),
                sdk.getCustomProcName(),
                sdk.getMetricType());
    }

    private CesNotificationMaskHierarchicalValue toHierarchicalValue(HierarchicalValue sdk) {
        if (sdk == null) {
            return null;
        }
        return new CesNotificationMaskHierarchicalValue(
                sdk.getCritical(),
                sdk.getMajor(),
                sdk.getMinor(),
                sdk.getInfo());
    }
}
