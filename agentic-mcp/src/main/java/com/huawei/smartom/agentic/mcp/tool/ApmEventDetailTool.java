/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code show_event_detail} 能力的 MCP 工具：获取单个调用链事件详情。
 *
 * <p>T29 根因诊断链第 4 步，工具命名、描述及语义遵循
 * {@code docs/specs/tools/show_event_detail.md}。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class ApmEventDetailTool {

    private final ApmTraceService service;

    /**
     * 构造 {@code ApmEventDetailTool} 实例。
     *
     * @param service APM 调用链查询服务
     */
    public ApmEventDetailTool(ApmTraceService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按四元组获取事件详情。
     *
     * @param traceId trace id
     * @param spanId  span id
     * @param eventId 事件 id
     * @param envId   环境 id
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "show_event_detail",
            description = """
                    Get the full detail of ONE call-chain event: its tags carry the root-cause \
                    data — exception class and message, SQL statement, HTTP status and more. \
                    ALL four parameters MUST be copied from a prior show_trace_events response \
                    (the event's trace_id / span_id / event_id-or-id / env_id) — do NOT invent \
                    them. Oversized values (full stack trace, full SQL) appear as clob \
                    references; fetch their text with show_clob_detail. Live data, not cached.""")
    public Object showEventDetail(
            @ToolParam(description = "Trace id, from the show_trace_events event.")
            String traceId,
            @ToolParam(description = "Span id, from the show_trace_events event.")
            String spanId,
            @ToolParam(description = "Event id, from the show_trace_events event "
                    + "(its event_id or id field).")
            String eventId,
            @ToolParam(description = "Environment id, from the show_trace_events event.")
            Long envId) {
        ApmEventDetailRequest req = new ApmEventDetailRequest(traceId, spanId, eventId, envId);
        return ToolCallSupport.execute("show_event_detail", () -> service.showEventDetail(req));
    }
}
