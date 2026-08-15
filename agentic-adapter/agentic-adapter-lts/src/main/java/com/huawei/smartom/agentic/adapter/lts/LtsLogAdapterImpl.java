/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogGroupsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsLogGroup;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsLogEntry;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsLogStream;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.lts.v2.LtsClient;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextRequestBody;
import com.huaweicloud.sdk.lts.v2.model.ListLogContextResponse;
import com.huaweicloud.sdk.lts.v2.model.ListLogGroupsRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogGroupsResponse;
import com.huaweicloud.sdk.lts.v2.model.ListLogStreamsRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogStreamsResponse;
import com.huaweicloud.sdk.lts.v2.model.ListLogsRequest;
import com.huaweicloud.sdk.lts.v2.model.ListLogsResponse;
import com.huaweicloud.sdk.lts.v2.model.LogContents;
import com.huaweicloud.sdk.lts.v2.model.QueryLtsLogParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于华为云 LTS Java SDK v2 的 {@link LtsLogAdapter} 默认实现。
 *
 * <p>本类只承担对外 DTO 与 SDK 请求 / 响应类之间的映射。所有 SDK 异常都通过
 * {@link HuaweiCloudInvocation} 统一转换为 {@code SmartomException}。
 *
 * @author h00884391
 * @since 2026-06-03
 */
@Component
public class LtsLogAdapterImpl implements LtsLogAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(LtsLogAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "lts-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_LIST_LOGS = "lts.listLogs";
    private static final String API_LIST_LOG_CONTEXT = "lts.listLogContext";
    private static final String API_LIST_LOG_GROUPS = "lts.listLogGroups";
    private static final String API_LIST_LOG_STREAMS = "lts.listLogStreams";

    private final LtsClient ltsClient;
    private final HuaweiCloudInvocation invocation;

    /**
     * 构造 {@code LtsLogAdapterImpl}，注入华为云 LTS SDK 客户端与统一的容错调用帮助类。
     *
     * @param ltsClient  已配置的华为云 LTS SDK 客户端
     * @param invocation 负责限流、重试以及 SDK 异常映射的调用帮助类
     */
    public LtsLogAdapterImpl(LtsClient ltsClient, HuaweiCloudInvocation invocation) {
        this.ltsClient = ltsClient;
        this.invocation = invocation;
    }

    @Override
    public LtsListLogGroupsResponse listLogGroups() {
        LOG.info("lts.listLogGroups start");
        ListLogGroupsResponse response = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_LOG_GROUPS,
                () -> ltsClient.listLogGroups(new ListLogGroupsRequest()));
        List<LtsLogGroup> groups = response.getLogGroups() == null ? List.of()
                : response.getLogGroups().stream().map(group -> new LtsLogGroup(
                        group.getCreationTime(), group.getLogGroupName(), group.getLogGroupId(),
                        group.getTtlInDays(), group.getTag(), group.getLogGroupNameAlias())).toList();
        return new LtsListLogGroupsResponse(groups);
    }

    @Override
    public LtsListLogStreamsResponse listLogStreams(LtsListLogStreamsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("lts.listLogStreams start, logGroupName={}, logStreamName={}",
                request.logGroupName(), request.logStreamName());
        ListLogStreamsRequest sdkRequest = new ListLogStreamsRequest()
                .withLogGroupName(request.logGroupName()).withLogStreamName(request.logStreamName());
        ListLogStreamsResponse response = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_LOG_STREAMS,
                () -> ltsClient.listLogStreams(sdkRequest));
        List<LtsLogStream> streams = response.getLogStreams() == null ? List.of()
                : response.getLogStreams().stream().map(stream -> new LtsLogStream(
                        stream.getCreationTime(), stream.getLogStreamId(), stream.getLogStreamName(),
                        stream.getLogStreamNameAlias(), stream.getTag(), stream.getFilterCount(),
                        stream.getWhetherLogStorage(), stream.getHotColdSeparation(), stream.getAuthWebTracking(),
                        stream.getTtlInDays(), stream.getHotStorageDays(), stream.getLogGroupId(),
                        stream.getIsFavorite())).toList();
        return new LtsListLogStreamsResponse(streams);
    }

    @Override
    public LtsListLogsResponse listLogs(LtsListLogsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("lts.listLogs start, logGroupId={}, logStreamId={}, "
                        + "startTimeMillis={}, endTimeMillis={}, keywords={}, query={}, limit={}",
                request.logGroupId(), request.logStreamId(),
                request.startTimeMillis(), request.endTimeMillis(),
                request.keywords(), request.query(), request.limit());

        ListLogsRequest sdkRequest = toListLogsSdkRequest(request);
        ListLogsResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_LOGS,
                () -> ltsClient.listLogs(sdkRequest));

        return toListLogsResponseDto(sdkResponse);
    }

    @Override
    public LtsListLogContextResponse listLogContext(LtsListLogContextRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("lts.listLogContext start, logGroupId={}, logStreamId={}, "
                        + "lineNum={}, backwardsSize={}, forwardsSize={}",
                request.logGroupId(), request.logStreamId(), request.lineNum(),
                request.backwardsSize(), request.forwardsSize());

        ListLogContextRequest sdkRequest = toListLogContextSdkRequest(request);
        ListLogContextResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_LOG_CONTEXT,
                () -> ltsClient.listLogContext(sdkRequest));

        return toListLogContextResponseDto(sdkResponse);
    }

    private ListLogsRequest toListLogsSdkRequest(LtsListLogsRequest request) {
        QueryLtsLogParams body = new QueryLtsLogParams()
                .withLabels(request.labels())
                .withKeywords(request.keywords())
                .withQuery(request.query())
                .withIsAnalysisQuery(request.isAnalysisQuery())
                .withIsCount(request.isCount())
                .withLimit(request.limit())
                .withIsDesc(request.isDesc())
                .withHighlight(request.highlight())
                .withIsIterative(request.isIterative())
                .withLineNum(request.lineNum())
                .withTime(request.cursorTime())
                .withScrollId(request.scrollId());
        if (request.startTimeMillis() != null) {
            body.setStartTime(String.valueOf(request.startTimeMillis()));
        }
        if (request.endTimeMillis() != null) {
            body.setEndTime(String.valueOf(request.endTimeMillis()));
        }
        if (request.searchType() != null) {
            body.setSearchType(QueryLtsLogParams.SearchTypeEnum.fromValue(request.searchType()));
        }
        return new ListLogsRequest()
                .withLogGroupId(request.logGroupId())
                .withLogStreamId(request.logStreamId())
                .withBody(body);
    }

    private LtsListLogsResponse toListLogsResponseDto(ListLogsResponse sdkResp) {
        List<LtsLogEntry> logs = toLogEntries(sdkResp.getLogs());
        List<Object> analysisLogs = sdkResp.getAnalysisLogs() == null
                ? Collections.emptyList()
                : sdkResp.getAnalysisLogs();
        return new LtsListLogsResponse(
                sdkResp.getCount(),
                logs,
                sdkResp.getIsQueryComplete(),
                analysisLogs);
    }

    private ListLogContextRequest toListLogContextSdkRequest(LtsListLogContextRequest request) {
        ListLogContextRequestBody body = new ListLogContextRequestBody()
                .withLineNum(request.lineNum())
                .withTime(request.cursorTime())
                .withBackwardsSize(request.backwardsSize())
                .withForwardsSize(request.forwardsSize())
                .withScrollId(request.scrollId());
        return new ListLogContextRequest()
                .withLogGroupId(request.logGroupId())
                .withLogStreamId(request.logStreamId())
                .withBody(body);
    }

    private LtsListLogContextResponse toListLogContextResponseDto(ListLogContextResponse sdkResp) {
        return new LtsListLogContextResponse(
                toLogEntries(sdkResp.getLogs()),
                sdkResp.getTotalCount(),
                sdkResp.getBackwardsCount(),
                sdkResp.getForwardsCount(),
                sdkResp.getIsQueryComplete());
    }

    private List<LtsLogEntry> toLogEntries(List<LogContents> sdkLogs) {
        if (sdkLogs == null) {
            return Collections.emptyList();
        }
        return sdkLogs.stream()
                .map(sdk -> new LtsLogEntry(sdk.getContent(), sdk.getLineNum(), sdk.getLabels()))
                .toList();
    }
}
