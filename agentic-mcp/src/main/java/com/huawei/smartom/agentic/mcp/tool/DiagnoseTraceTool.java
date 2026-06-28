/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.apm.DiagnoseTraceRequest;
import com.huawei.smartom.agentic.monitoring.apm.DiagnoseTraceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code diagnose_trace} 一站式 trace 故障诊断能力的 MCP 工具。
 *
 * <p>仅凭一个 trace_id，在一次调用内完成"拓扑 + 调用链事件 + 出错/最慢 span 详情 + clob 全文"的
 * 编排下钻，直接产出聚焦根因的诊断包，免去 Agent 手工串联
 * {@code get_service_topology} → {@code show_trace_events} → {@code show_event_detail} →
 * {@code show_clob_detail} 四步。
 *
 * @author huangxinqi
 * @since 2026-06-18
 */
@Component
public class DiagnoseTraceTool implements McpTool {

    private final DiagnoseTraceService service;

    /**
     * 构造 {@code DiagnoseTraceTool} 实例。
     *
     * @param service trace 诊断编排服务
     */
    public DiagnoseTraceTool(DiagnoseTraceService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：对给定 trace 一步完成故障诊断。
     *
     * @param traceId          APM trace id，必填，来自告警载荷或 {@code query_traces}
     * @param businessId       APM 业务 id，可选；为空回落到服务端配置默认值（仅 clob 下钻用）
     * @param maxSuspectEvents 最多深挖的可疑 event 数，可选，默认 5
     * @param includeSlowest   无出错 event 时是否仍纳入最慢的若干 span，可选，默认 true
     * @return 聚合诊断包；输入校验失败时返回
     *         {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "diagnose_trace",
            description = """
                    One-shot APM trace fault diagnosis. Give a single trace_id and this tool \
                    orchestrates the whole chain for you: it fetches the call-graph topology and \
                    ALL call-chain events concurrently, picks the suspect spans (errored first, \
                    then slowest), drills into each suspect's event detail, and resolves the \
                    oversized clob fields (full exception stack trace / full SQL) into text. \
                    Returns a root-cause-focused bundle: {topology, total/error event counts, \
                    suspects[](spanId, eventId, envId, type, method, className, timeUsed, \
                    hasError, errorReasons, selectedReason, tags, clobs[]), errors[]}. Each \
                    stage is independent — a failing stage is recorded in 'errors' and nulls its \
                    field rather than failing the whole call. Use this instead of manually \
                    chaining get_service_topology -> show_trace_events -> show_event_detail -> \
                    show_clob_detail. trace_id comes from the alarm payload or query_traces — do \
                    NOT invent it. Live data, not cached.""")
    public Object diagnoseTrace(
            @ToolParam(description = "APM trace_id, from the alarm payload or query_traces.")
            String traceId,
            @ToolParam(description = "APM business id; falls back to the server-configured "
                    + "default when null. Only used for clob drill-down.", required = false)
            Long businessId,
            @ToolParam(description = "Max number of suspect events to drill into. Default 5.",
                    required = false)
            Integer maxSuspectEvents,
            @ToolParam(description = "Whether to also include the slowest spans when no errored "
                    + "event exists. Default true.", required = false)
            Boolean includeSlowest) {

        DiagnoseTraceRequest req = new DiagnoseTraceRequest(
                traceId, businessId, maxSuspectEvents, includeSlowest);
        return ToolCallSupport.execute("diagnose_trace", () -> service.diagnose(req));
    }
}
