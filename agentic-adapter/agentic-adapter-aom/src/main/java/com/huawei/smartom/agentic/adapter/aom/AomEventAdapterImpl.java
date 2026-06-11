/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomEvent;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEventMetadataRelation;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEventPageInfo;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.model.EventQueryParam2;
import com.huaweicloud.sdk.aom.v2.model.EventQueryParam2Sort;
import com.huaweicloud.sdk.aom.v2.model.ListEventModel;
import com.huaweicloud.sdk.aom.v2.model.ListEventsRequest;
import com.huaweicloud.sdk.aom.v2.model.ListEventsResponse;
import com.huaweicloud.sdk.aom.v2.model.PageInfo;
import com.huaweicloud.sdk.aom.v2.model.RelationModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link AomEventAdapter} 的默认实现：封装华为云 AOM v2 {@code ListEvents} 调用，
 * 经 {@link HuaweiCloudInvocation} 套用限流（{@code aom-readonly}）、重试与异常映射。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class AomEventAdapterImpl implements AomEventAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(AomEventAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "aom-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_LIST_EVENTS = "aom.listEvents";

    private final AomClient aomClient;
    private final HuaweiCloudInvocation invocation;

    /**
     * 构造 {@code AomEventAdapterImpl}。
     *
     * @param aomClient  已配置好的华为云 AOM SDK 客户端
     * @param invocation 提供限流、重试以及 SDK 异常映射能力的调用助手
     */
    public AomEventAdapterImpl(AomClient aomClient, HuaweiCloudInvocation invocation) {
        this.aomClient = aomClient;
        this.invocation = invocation;
    }

    @Override
    public AomListEventsResponse listEvents(AomListEventsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("aom.listEvents start, type={}, timeRange={}, search={}, limit={}, marker={}",
                request.type(), request.timeRange(), request.search(),
                request.limit(), request.marker());

        ListEventsRequest sdkRequest = toListEventsSdkRequest(request);
        ListEventsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_EVENTS,
                () -> aomClient.listEvents(sdkRequest));

        return toListEventsResponseDto(sdkResponse);
    }

    private ListEventsRequest toListEventsSdkRequest(AomListEventsRequest request) {
        EventQueryParam2 body = new EventQueryParam2()
                .withTimeRange(request.timeRange())
                .withStep(request.step())
                .withSearch(request.search());
        if (request.sortOrderBy() != null || request.sortOrder() != null) {
            body.withSort(new EventQueryParam2Sort()
                    .withOrderBy(request.sortOrderBy())
                    .withOrder(request.sortOrder() == null
                            ? null
                            : EventQueryParam2Sort.OrderEnum.fromValue(request.sortOrder())));
        }
        if (request.metadataRelation() != null && !request.metadataRelation().isEmpty()) {
            body.withMetadataRelation(request.metadataRelation().stream()
                    .map(this::toSdkRelation)
                    .toList());
        }
        return new ListEventsRequest()
                .withType(request.type() == null
                        ? null
                        : ListEventsRequest.TypeEnum.fromValue(request.type().getValue()))
                .withLimit(request.limit())
                .withMarker(request.marker())
                .withBody(body);
    }

    private RelationModel toSdkRelation(AomEventMetadataRelation relation) {
        return new RelationModel()
                .withKey(relation.key())
                .withValue(relation.value())
                .withRelation(relation.relation() == null
                        ? null
                        : RelationModel.RelationEnum.fromValue(relation.relation()));
    }

    private AomListEventsResponse toListEventsResponseDto(ListEventsResponse sdkResp) {
        List<ListEventModel> sdkEvents =
                sdkResp.getEvents() == null ? Collections.emptyList() : sdkResp.getEvents();
        List<AomEvent> events = sdkEvents.stream()
                .map(this::toEventDto)
                .toList();
        return new AomListEventsResponse(events, toPageInfoDto(sdkResp.getPageInfo()));
    }

    private AomEvent toEventDto(ListEventModel sdk) {
        return new AomEvent(
                sdk.getId(),
                sdk.getEventSn(),
                sdk.getStartsAt(),
                sdk.getEndsAt(),
                sdk.getArrivesAt(),
                sdk.getTimeout(),
                sdk.getEnterpriseProjectId(),
                sdk.getMetadata(),
                sdk.getAnnotations(),
                sdk.getAttachRule(),
                sdk.getPolicy());
    }

    private AomEventPageInfo toPageInfoDto(PageInfo sdk) {
        if (sdk == null) {
            return null;
        }
        return new AomEventPageInfo(
                sdk.getCurrentCount(),
                sdk.getPreviousMarker(),
                sdk.getNextMarker());
    }
}
