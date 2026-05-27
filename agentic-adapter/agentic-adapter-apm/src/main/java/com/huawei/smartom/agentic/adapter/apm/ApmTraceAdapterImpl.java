/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSpan;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTopologyLine;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTopologyNode;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.ClientSpanInfo;
import com.huaweicloud.sdk.apm.v1.model.ShowSpanSearchRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowSpanSearchResponse;
import com.huaweicloud.sdk.apm.v1.model.ShowTopologyRequest;
import com.huaweicloud.sdk.apm.v1.model.ShowTopologyResponse;
import com.huaweicloud.sdk.apm.v1.model.TraceSearchParam;
import com.huaweicloud.sdk.apm.v1.model.TraceTopologyLine;
import com.huaweicloud.sdk.apm.v1.model.TraceTopologyLineInfo;
import com.huaweicloud.sdk.apm.v1.model.TraceTopologyNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于华为云 Java SDK v3.1.177 的 {@link ApmTraceAdapter} 实现。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class ApmTraceAdapterImpl implements ApmTraceAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ApmTraceAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "apm-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_SHOW_SPAN_SEARCH = "apm.showSpanSearch";
    private static final String API_SHOW_TOPOLOGY = "apm.showTopology";

    private final ApmClient apmClient;
    private final HuaweiCloudInvocation invocation;
    private final HuaweiCloudProperties properties;

    /**
     * 构造 {@code ApmTraceAdapterImpl}。
     *
     * @param apmClient  已配置好的华为云 APM SDK 客户端
     * @param invocation 限流 / 重试 / 异常映射调用助手
     * @param properties 华为云配置，用于读取 APM 默认 business id
     */
    public ApmTraceAdapterImpl(
            ApmClient apmClient,
            HuaweiCloudInvocation invocation,
            HuaweiCloudProperties properties) {
        this.apmClient = apmClient;
        this.invocation = invocation;
        this.properties = properties;
    }

    @Override
    public ApmQueryTracesResponse queryTraces(ApmQueryTracesRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Long businessId = request.businessId() == null
                ? properties.getApmBusinessId()
                : request.businessId();
        LOG.info("apm.showSpanSearch start, businessId={}, traceId={}, source={}, page={}, pageSize={}",
                businessId, request.traceId(), request.source(), request.page(), request.pageSize());

        TraceSearchParam body = new TraceSearchParam()
                .withRegion(properties.getApmRegion())
                .withPage(request.page())
                .withPageSize(request.pageSize());
        if (request.startTimeString() != null) {
            body.setStartTimeString(request.startTimeString());
        }
        if (request.endTimeString() != null) {
            body.setEndTimeString(request.endTimeString());
        }
        if (request.traceId() != null) {
            body.setTraceId(request.traceId());
        }
        if (request.source() != null) {
            body.setSource(request.source());
        }
        if (request.hasError() != null) {
            body.setHasError(request.hasError());
        }
        if (request.timeUsedMin() != null) {
            body.setTimeUsedMin(request.timeUsedMin());
        }

        ShowSpanSearchRequest sdkRequest = new ShowSpanSearchRequest().withBody(body);
        if (businessId != null) {
            sdkRequest.setXBusinessId(businessId);
        }

        ShowSpanSearchResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_SHOW_SPAN_SEARCH,
                () -> apmClient.showSpanSearch(sdkRequest));

        List<ClientSpanInfo> sdkSpans =
                sdkResponse.getSpanInfoList() == null ? Collections.emptyList() : sdkResponse.getSpanInfoList();
        List<ApmSpan> spans = sdkSpans.stream()
                .map(this::toSpan)
                .toList();
        return new ApmQueryTracesResponse(sdkResponse.getTotal(), spans);
    }

    @Override
    public ApmGetTopologyResponse getTopology(ApmGetTopologyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("apm.showTopology start, traceId={}", request.traceId());

        ShowTopologyRequest sdkRequest = new ShowTopologyRequest()
                .withTraceId(request.traceId());

        ShowTopologyResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_SHOW_TOPOLOGY,
                () -> apmClient.showTopology(sdkRequest));

        List<TraceTopologyNode> sdkNodes =
                sdkResponse.getNodeList() == null ? Collections.emptyList() : sdkResponse.getNodeList();
        List<ApmTopologyNode> nodes = sdkNodes.stream()
                .map(node -> new ApmTopologyNode(node.getNodeId(), node.getNodeName(), node.getHint()))
                .toList();

        List<TraceTopologyLine> sdkLines =
                sdkResponse.getLineList() == null ? Collections.emptyList() : sdkResponse.getLineList();
        List<ApmTopologyLine> lines = sdkLines.stream()
                .map(this::toLine)
                .toList();

        return new ApmGetTopologyResponse(sdkResponse.getGlobalTraceId(), nodes, lines);
    }

    private ApmSpan toSpan(ClientSpanInfo sdk) {
        Map<String, String> tags = sdk.getTags() == null ? Map.of() : Map.copyOf(sdk.getTags());
        return new ApmSpan(
                sdk.getTraceId(),
                sdk.getSpanId(),
                sdk.getGlobalTraceId(),
                sdk.getSource(),
                sdk.getRealSource(),
                sdk.getClassName(),
                sdk.getStartTime(),
                sdk.getTimeUsed(),
                sdk.getCode(),
                sdk.getHasError(),
                sdk.getErrorReasons(),
                sdk.getHttpMethod(),
                tags);
    }

    private ApmTopologyLine toLine(TraceTopologyLine sdk) {
        TraceTopologyLineInfo client = sdk.getClientInfo();
        TraceTopologyLineInfo server = sdk.getServerInfo();
        return new ApmTopologyLine(
                sdk.getStartNodeId(),
                sdk.getEndNodeId(),
                sdk.getSpanId(),
                client == null ? null : client.getStartTime(),
                client == null ? null : client.getTimeUsed(),
                server == null ? null : server.getStartTime(),
                server == null ? null : server.getTimeUsed(),
                sdk.getHint());
    }
}
