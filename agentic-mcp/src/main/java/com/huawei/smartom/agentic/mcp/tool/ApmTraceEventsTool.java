/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code show_trace_events} 能力的 MCP 工具：获取一个 trace 的全部调用链事件序列。
 *
 * <p>T29 根因诊断链第 3 步，工具命名、描述及语义遵循
 * {@code docs/specs/tools/show_trace_events.md}。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class ApmTraceEventsTool {

    private final ApmTraceService service;

    /**
     * 构造 {@code ApmTraceEventsTool} 实例。
     *
     * @param service APM 调用链查询服务
     */
    public ApmTraceEventsTool(ApmTraceService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按 trace_id 获取全部调用链事件。
     *
     * @param traceId trace id
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "show_trace_events",
            description = """
                    Get ALL call-chain events of one APM trace: the per-span sequence of method \
                    calls, SQL statements and remote invocations with duration, status code and \
                    error flags. This is the bridge from "which component is slow/failing" \
                    (query_traces / get_service_topology) down to "which step inside it". \
                    trace_id comes from the alarm payload or a query_traces result — do NOT \
                    invent it. For each suspicious event take its (trace_id, span_id, \
                    event_id/id, env_id) and call show_event_detail next. Live data, not \
                    cached.""")
    public Object showTraceEvents(
            @ToolParam(description = "APM trace id, from the alarm payload or query_traces.")
            String traceId) {
        return ToolCallSupport.execute("show_trace_events", () -> service.showTraceEvents(traceId));
    }
}
