/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentRequest;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code correlate_incident} 跨组件事故关联能力的 MCP 工具。
 *
 * <p>在一次调用内并发查询 CES 告警 / AOM 日志 / APM trace / APM 拓扑，
 * 帮助 Agent 在事故分析时快速汇总跨组件证据。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CorrelateIncidentTool implements McpTool {

    private final CorrelateIncidentService service;

    /**
     * 构造 {@code CorrelateIncidentTool} 实例。
     *
     * @param service 跨组件关联服务
     */
    public CorrelateIncidentTool(CorrelateIncidentService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：跨 CES / AOM / APM 进行事故关联查询。
     *
     * @param startTimeMillis 时间窗起点（UTC 毫秒）
     * @param endTimeMillis   时间窗终点（UTC 毫秒）
     * @param cesNamespace    CES 命名空间，可选
     * @param logCategory     AOM 日志类型，可选（为空则跳过日志分支）
     * @param logKeyword      AOM 日志关键字，可选
     * @param apmTraceId      APM trace id，可选（为空则跳过拓扑分支）
     * @param apmSource       APM 入口资源，可选
     * @return 各分支结果聚合（含 success / failure / skipped 状态）；
     *         输入校验失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "correlate_incident",
            description = """
                    Cross-component incident correlation: in a single call, concurrently query \
                    CES alarms, AOM logs, and APM traces (+ topology) within a time window. \
                    Use this when you need a fast cross-cut view of what was happening at the \
                    time of an incident — across infrastructure (CES), application logs (AOM), \
                    and request traces (APM). Each branch is independent: a failing branch does \
                    not affect the others, and any branch whose required input is missing is \
                    reported as 'skipped' rather than failing the whole call.""")
    public Object correlateIncident(
            @ToolParam(description = "Incident window start, UTC millis.")
            Long startTimeMillis,
            @ToolParam(description = "Incident window end, UTC millis. Must be > start.")
            Long endTimeMillis,
            @ToolParam(description = "CES namespace filter, e.g. SYS.ECS.", required = false)
            String cesNamespace,
            @ToolParam(description = "AOM log category: app_log / node_log / custom_log. "
                    + "If null, the log branch is skipped.", required = false)
            String logCategory,
            @ToolParam(description = "AOM log keyword (optional).", required = false)
            String logKeyword,
            @ToolParam(description = "APM trace_id. Required to populate the topology branch.",
                    required = false)
            String apmTraceId,
            @ToolParam(description = "APM entry source filter (optional).", required = false)
            String apmSource) {

        CorrelateIncidentRequest req = new CorrelateIncidentRequest(
                startTimeMillis, endTimeMillis, cesNamespace,
                logCategory, logKeyword, apmTraceId, apmSource);
        return ToolCallSupport.execute("correlate_incident", () -> service.correlate(req));
    }
}
