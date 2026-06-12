/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code query_traces} 能力的 MCP 工具：搜索 APM span。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class ApmTraceTool implements McpTool {

    private final ApmTraceService service;

    /**
     * 构造 {@code ApmTraceTool} 实例。
     *
     * @param service APM trace 查询服务
     */
    public ApmTraceTool(ApmTraceService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：搜索 APM span。
     *
     * @param businessId     APM 应用 id，可选；为空时使用配置项默认值
     * @param startTimeString 起始时间字符串，可选
     * @param endTimeString   结束时间字符串，可选
     * @param traceId         按 traceId 精确过滤，可选
     * @param source          入口资源模糊匹配，可选
     * @param hasError        是否仅返回出错 span，可选
     * @param timeUsedMin     最小耗时（毫秒），可选
     * @param page            页码，默认 1
     * @param pageSize        单页大小，默认 50
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "query_traces",
            description = """
                    Search APM (Application Performance Management) trace spans for a Huawei \
                    Cloud application. Use this when investigating latency / error issues — \
                    filter by traceId, entry url/method (source), time window, error flag, or \
                    minimum elapsed time. Returns matched span summaries (traceId, spanId, \
                    timing, error info, tags). Call get_service_topology afterwards with a \
                    specific traceId to see the call graph.""")
    public Object queryTraces(
            @ToolParam(description = "APM application id (x-business-id). Optional if a "
                    + "default is configured server-side.", required = false)
            Long businessId,
            @ToolParam(description = "Start time, format 'yyyy-MM-dd HH:mm:ss'.",
                    required = false)
            String startTimeString,
            @ToolParam(description = "End time, format 'yyyy-MM-dd HH:mm:ss'.",
                    required = false)
            String endTimeString,
            @ToolParam(description = "Exact trace id filter.", required = false)
            String traceId,
            @ToolParam(description = "Entry URL or method fuzzy match.", required = false)
            String source,
            @ToolParam(description = "When true, return only spans with errors.",
                    required = false)
            Boolean hasError,
            @ToolParam(description = "Minimum elapsed time in milliseconds.",
                    required = false)
            Long timeUsedMin,
            @ToolParam(description = "Page (1-based), default 1.", required = false)
            Integer page,
            @ToolParam(description = "Page size [1, 500], default 50.", required = false)
            Integer pageSize) {

        ApmQueryTracesRequest req = new ApmQueryTracesRequest(
                businessId, startTimeString, endTimeString, traceId,
                source, hasError, timeUsedMin, page, pageSize);
        return ToolCallSupport.execute("query_traces", () -> service.queryTraces(req));
    }
}
