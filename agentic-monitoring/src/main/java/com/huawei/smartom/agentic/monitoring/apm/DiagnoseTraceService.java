/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSpanEvent;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code diagnose_trace} 编排服务：从一个 trace_id 一步产出根因诊断包。
 *
 * <p>编排步骤：并发拉取调用图拓扑与全部调用链事件，从事件中挑出出错 / 最慢的可疑 span，
 * 再并发下钻每个可疑 span 的 event 详情与其中的 clob（堆栈 / SQL）全文。各阶段相互独立——
 * 单阶段失败仅记入 {@link DiagnoseStageError}，不中断整体，与 {@code correlate_incident} 一致。
 *
 * <p>线程模型：虚拟线程 {@code Executors.newVirtualThreadPerTaskExecutor()}（CLAUDE.md §4.4）。
 * 本服务仅编排既有 {@link ApmTraceService} 的只读能力，不直接触达 SDK。
 *
 * @author huangxinqi
 * @since 2026-06-18
 */
@Service
public class DiagnoseTraceService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnoseTraceService.class);

    private static final int DEFAULT_MAX_SUSPECTS = 5;

    private static final int MAX_SUSPECTS = 20;

    private static final String CLOB_ID_SUFFIX = "_clob_id";

    private final ApmTraceService apmTraceService;

    /**
     * 构造一个 {@code DiagnoseTraceService}。
     *
     * @param apmTraceService APM trace / 拓扑 / 事件 / clob 查询服务
     */
    public DiagnoseTraceService(ApmTraceService apmTraceService) {
        this.apmTraceService = apmTraceService;
    }

    /**
     * 对给定 trace 执行一站式故障诊断。
     *
     * @param request 入参，{@code traceId} 必填
     * @return 聚合诊断包（拓扑 + 事件概览 + 可疑事件详情与 clob + 阶段错误）
     * @throws InvalidParamException {@code request} 为空或 {@code traceId} 为空白
     */
    public DiagnoseTraceResponse diagnose(DiagnoseTraceRequest request) {
        validate(request);
        LOG.info("diagnose_trace start, traceId={}, maxSuspects={}, includeSlowest={}",
                request.traceId(), request.maxSuspectEvents(), request.includeSlowest());
        List<DiagnoseStageError> errors = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<ApmGetTopologyResponse> topoFuture =
                    CompletableFuture.supplyAsync(() -> topology(request.traceId(), errors), executor);
            CompletableFuture<ApmTraceEventsResponse> eventsFuture =
                    CompletableFuture.supplyAsync(() -> events(request.traceId(), errors), executor);
            ApmGetTopologyResponse topology = topoFuture.join();
            ApmTraceEventsResponse events = eventsFuture.join();
            return assemble(request, topology, events, errors);
        }
    }

    private DiagnoseTraceResponse assemble(DiagnoseTraceRequest request, ApmGetTopologyResponse topology,
            ApmTraceEventsResponse events, List<DiagnoseStageError> errors) {
        List<ApmSpanEvent> all = (events == null) ? List.of() : nullSafe(events.spanEventList());
        List<SuspectEvent> suspects = deepDive(selectSuspects(all, request), request, errors);
        Integer total = (events == null) ? null : all.size();
        Integer errorCount = (events == null) ? null : (int) all.stream().filter(this::isError).count();
        LOG.info("diagnose_trace done, traceId={}, totalEvents={}, errorEvents={}, suspects={}, stageErrors={}",
                request.traceId(), total, errorCount, suspects.size(), errors.size());
        return new DiagnoseTraceResponse(request.traceId(), topology, total, errorCount, suspects, errors);
    }

    private ApmGetTopologyResponse topology(String traceId, List<DiagnoseStageError> errors) {
        try {
            return apmTraceService.getTopology(new ApmGetTopologyRequest(traceId));
        }
        catch (SmartomException e) {
            errors.add(toStageError("topology", e));
            return null;
        }
    }

    private ApmTraceEventsResponse events(String traceId, List<DiagnoseStageError> errors) {
        try {
            return apmTraceService.showTraceEvents(traceId);
        }
        catch (SmartomException e) {
            errors.add(toStageError("events", e));
            return null;
        }
    }

    /**
     * 从全部事件中挑出可疑 span：先取所有出错事件，再（可选）按耗时倒序补足最慢的，去重后截断到上限。
     *
     * @param all     该 trace 的全部调用链事件
     * @param request 入参，提供上限与是否纳入最慢
     * @return 去重并截断后的可疑事件列表，按"出错优先、其次最慢"排序
     */
    private List<ApmSpanEvent> selectSuspects(List<ApmSpanEvent> all, DiagnoseTraceRequest request) {
        int max = (request.maxSuspectEvents() == null || request.maxSuspectEvents() <= 0)
                ? DEFAULT_MAX_SUSPECTS : request.maxSuspectEvents();
        boolean includeSlowest = request.includeSlowest() == null || request.includeSlowest();
        LinkedHashMap<String, ApmSpanEvent> picked = new LinkedHashMap<>();
        for (ApmSpanEvent event : all) {
            if (isError(event)) {
                picked.put(keyOf(event), event);
            }
        }
        if (includeSlowest) {
            all.stream()
                    .filter(event -> event.timeUsed() != null)
                    .sorted(Comparator.comparingLong(ApmSpanEvent::timeUsed).reversed())
                    .forEach(event -> picked.putIfAbsent(keyOf(event), event));
        }
        return picked.values().stream().limit(max).toList();
    }

    private List<SuspectEvent> deepDive(List<ApmSpanEvent> selected, DiagnoseTraceRequest request,
            List<DiagnoseStageError> errors) {
        return selected.stream()
                .map(event -> buildSuspect(event, request, errors))
                .toList();
    }

    private SuspectEvent buildSuspect(ApmSpanEvent base, DiagnoseTraceRequest request,
            List<DiagnoseStageError> errors) {
        String reason = isError(base) ? "error" : "slow";
        ApmSpanEvent detail = fetchDetail(base, errors);
        ApmSpanEvent info = (detail != null) ? detail : base;
        Map<String, String> tags = (detail != null) ? detail.tags() : null;
        List<ResolvedClob> clobs = resolveClobs(detail, request.businessId(), errors);
        return new SuspectEvent(
                base.spanId(), eventIdOf(base), base.envId(),
                info.type(), info.method(), info.className(),
                info.timeUsed(), info.code(), info.hasError(), info.errorReasons(),
                reason, tags, clobs);
    }

    private ApmSpanEvent fetchDetail(ApmSpanEvent base, List<DiagnoseStageError> errors) {
        try {
            ApmEventDetailResponse resp = apmTraceService.showEventDetail(new ApmEventDetailRequest(
                    base.traceId(), base.spanId(), eventIdOf(base), base.envId()));
            return (resp == null) ? null : resp.eventInfo();
        }
        catch (SmartomException e) {
            errors.add(toStageError("detail:" + base.spanId(), e));
            return null;
        }
    }

    private List<ResolvedClob> resolveClobs(ApmSpanEvent detail, Long businessId,
            List<DiagnoseStageError> errors) {
        if (detail == null || detail.tags() == null) {
            return List.of();
        }
        List<ResolvedClob> resolved = new ArrayList<>();
        for (Map.Entry<String, String> tag : detail.tags().entrySet()) {
            if (tag.getKey() == null || !tag.getKey().endsWith(CLOB_ID_SUFFIX)) {
                continue;
            }
            resolved.add(fetchClob(tag.getKey(), tag.getValue(), detail.envId(), businessId, errors));
        }
        return resolved;
    }

    private ResolvedClob fetchClob(String tagKey, String clobId, Long envId, Long businessId,
            List<DiagnoseStageError> errors) {
        try {
            ApmClobDetailResponse resp = apmTraceService.showClobDetail(
                    new ApmClobDetailRequest(businessId, envId, clobId));
            return new ResolvedClob(tagKey, clobId, (resp == null) ? null : resp.clobString());
        }
        catch (SmartomException e) {
            errors.add(toStageError("clob:" + clobId, e));
            return new ResolvedClob(tagKey, clobId, null);
        }
    }

    private boolean isError(ApmSpanEvent event) {
        return Boolean.TRUE.equals(event.hasError());
    }

    private static String keyOf(ApmSpanEvent event) {
        return event.spanId() + "|" + eventIdOf(event);
    }

    private static String eventIdOf(ApmSpanEvent event) {
        return (event.eventId() != null) ? event.eventId() : event.id();
    }

    private static List<ApmSpanEvent> nullSafe(List<ApmSpanEvent> list) {
        return (list == null) ? List.of() : list;
    }

    private static DiagnoseStageError toStageError(String stage, SmartomException ex) {
        return new DiagnoseStageError(stage, ex.getErrorCode(),
                ex.getErrorCode().isRetryable(), ex.getMessage(), ex.getUpstreamTraceId());
    }

    private static void validate(DiagnoseTraceRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new InvalidParamException("trace_id is required");
        }
        if (request.maxSuspectEvents() != null
                && (request.maxSuspectEvents() < 1 || request.maxSuspectEvents() > MAX_SUSPECTS)) {
            throw new InvalidParamException("max_suspect_events must be in [1, " + MAX_SUSPECTS + "]");
        }
    }
}
