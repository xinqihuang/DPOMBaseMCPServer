/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code get_service_topology} 能力的 MCP 工具：返回指定 traceId 的调用链拓扑。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class ApmTopologyTool {

    private final ApmTraceService service;

    /**
     * 构造 {@code ApmTopologyTool} 实例。
     *
     * @param service APM trace / 拓扑查询服务
     */
    public ApmTopologyTool(ApmTraceService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：返回 traceId 对应的调用链拓扑（节点 + 边）。
     *
     * @param traceId 调用链 traceId
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "get_service_topology",
            description = """
                    Get the call-graph topology for a specific APM trace. Returns the list of \
                    service nodes and the directed edges (with client/server timing) reconstructed \
                    from the trace's spans. Use this after query_traces to visualise how a \
                    request flowed across services and where time was spent. Required: a valid \
                    trace_id from APM.""")
    public Object getServiceTopology(
            @ToolParam(description = "APM trace_id (from query_traces output).")
            String traceId) {

        ApmGetTopologyRequest req = new ApmGetTopologyRequest(traceId);
        return ToolCallSupport.execute("get_service_topology", () -> service.getTopology(req));
    }
}
