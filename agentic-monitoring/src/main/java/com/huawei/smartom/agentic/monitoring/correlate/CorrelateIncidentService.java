/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.correlate;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.aom.AomLogService;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;
import com.huawei.smartom.agentic.monitoring.ces.CesAlarmService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 跨组件事故关联编排：在一次调用内并发查询 CES 告警、AOM 日志、APM trace 与拓扑。
 *
 * <p>每个分支独立运行：单个分支失败不会阻塞其他分支，最终在
 * {@link CorrelateIncidentResponse} 中分别记录 success / failure / skipped 三态。
 *
 * <p>线程模型：使用虚拟线程 {@code Executors.newVirtualThreadPerTaskExecutor()}，与
 * CLAUDE.md §4.4 保持一致。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class CorrelateIncidentService {

    private static final Logger LOG = LoggerFactory.getLogger(CorrelateIncidentService.class);

    private final CesAlarmService cesAlarmService;
    private final AomLogService aomLogService;
    private final ApmTraceService apmTraceService;

    /**
     * 构造一个 {@code CorrelateIncidentService}。
     *
     * @param cesAlarmService CES 告警查询服务
     * @param aomLogService   AOM 日志查询服务
     * @param apmTraceService APM trace / 拓扑查询服务
     */
    public CorrelateIncidentService(
            CesAlarmService cesAlarmService,
            AomLogService aomLogService,
            ApmTraceService apmTraceService) {
        this.cesAlarmService = cesAlarmService;
        this.aomLogService = aomLogService;
        this.apmTraceService = apmTraceService;
    }

    /**
     * 执行跨组件事故关联查询。
     *
     * @param request 入参，{@code startTimeMillis < endTimeMillis} 必须满足
     * @return 各分支结果聚合
     * @throws InvalidParamException 时间窗参数缺失或非法
     */
    public CorrelateIncidentResponse correlate(CorrelateIncidentRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        if (request.startTimeMillis() == null || request.endTimeMillis() == null) {
            throw new InvalidParamException("start_time_millis and end_time_millis are required");
        }
        if (request.endTimeMillis() <= request.startTimeMillis()) {
            throw new InvalidParamException("end_time_millis must be > start_time_millis");
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<CorrelateBranchResult> alarmsFuture =
                    CompletableFuture.supplyAsync(() -> runCesAlarms(request), executor);
            CompletableFuture<CorrelateBranchResult> logsFuture =
                    CompletableFuture.supplyAsync(() -> runAomLogs(request), executor);
            CompletableFuture<CorrelateBranchResult> tracesFuture =
                    CompletableFuture.supplyAsync(() -> runApmTraces(request), executor);
            CompletableFuture<CorrelateBranchResult> topologyFuture =
                    CompletableFuture.supplyAsync(() -> runApmTopology(request), executor);

            CompletableFuture.allOf(alarmsFuture, logsFuture, tracesFuture, topologyFuture).join();

            return new CorrelateIncidentResponse(
                    waitFor(alarmsFuture, "ces_alarms"),
                    waitFor(logsFuture, "aom_logs"),
                    waitFor(tracesFuture, "apm_traces"),
                    waitFor(topologyFuture, "apm_topology"));
        }
    }

    private CorrelateBranchResult runCesAlarms(CorrelateIncidentRequest request) {
        return runBranch("ces_alarms", () -> {
            // CES v2 ListAlarmHistories 要求 from/to 为 ISO 8601 datetime（含偏移量），
            // 与 v1 的 millis-string 形态不同，详见 docs/tasks/T19-lossless-dto-remediation.md
            CesListAlarmsRequest req = new CesListAlarmsRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    request.cesNamespace(),
                    toIsoOffsetDateTime(request.startTimeMillis()),
                    toIsoOffsetDateTime(request.endTimeMillis()),
                    0,
                    100);
            return cesAlarmService.listAlarms(req);
        });
    }

    private static String toIsoOffsetDateTime(long epochMillis) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC));
    }

    private CorrelateBranchResult runAomLogs(CorrelateIncidentRequest request) {
        if (request.logCategory() == null || request.logCategory().isBlank()) {
            return CorrelateBranchResult.ofSkipped();
        }
        return runBranch("aom_logs", () -> {
            AomQueryLogsRequest req = new AomQueryLogsRequest(
                    request.logCategory(),
                    request.startTimeMillis(),
                    request.endTimeMillis(),
                    request.logKeyword(),
                    100,
                    Boolean.TRUE);
            return aomLogService.queryLogs(req);
        });
    }

    private CorrelateBranchResult runApmTraces(CorrelateIncidentRequest request) {
        boolean noTraceId = request.apmTraceId() == null || request.apmTraceId().isBlank();
        boolean noSource = request.apmSource() == null || request.apmSource().isBlank();
        if (noTraceId && noSource) {
            return CorrelateBranchResult.ofSkipped();
        }
        return runBranch("apm_traces", () -> {
            ApmQueryTracesRequest req = new ApmQueryTracesRequest(
                    null,
                    null,
                    null,
                    request.apmTraceId(),
                    request.apmSource(),
                    null,
                    null,
                    1,
                    50);
            return apmTraceService.queryTraces(req);
        });
    }

    private CorrelateBranchResult runApmTopology(CorrelateIncidentRequest request) {
        if (request.apmTraceId() == null || request.apmTraceId().isBlank()) {
            return CorrelateBranchResult.ofSkipped();
        }
        return runBranch("apm_topology",
                () -> apmTraceService.getTopology(new ApmGetTopologyRequest(request.apmTraceId())));
    }

    private CorrelateBranchResult runBranch(String label, Supplier<Object> call) {
        try {
            return CorrelateBranchResult.success(call.get());
        }
        catch (SmartomException e) {
            LOG.warn("correlate_incident branch {} failed, errorCode={}, upstreamTraceId={}",
                    label, e.getErrorCode(), e.getUpstreamTraceId());
            return CorrelateBranchResult.failure(new CorrelateError(
                    e.getErrorCode(),
                    e.getErrorCode().isRetryable(),
                    e.getMessage(),
                    e.getUpstreamTraceId()));
        }
    }

    private CorrelateBranchResult waitFor(CompletableFuture<CorrelateBranchResult> future, String label) {
        try {
            return future.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("correlate_incident branch {} interrupted", label);
            return CorrelateBranchResult.failure(new CorrelateError(
                    com.huawei.smartom.agentic.common.error.ErrorCode.INTERNAL,
                    false,
                    "interrupted",
                    null));
        }
        catch (ExecutionException e) {
            LOG.warn("correlate_incident branch {} unexpected execution failure", label, e);
            return CorrelateBranchResult.failure(new CorrelateError(
                    com.huawei.smartom.agentic.common.error.ErrorCode.INTERNAL,
                    false,
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage(),
                    null));
        }
    }
}
