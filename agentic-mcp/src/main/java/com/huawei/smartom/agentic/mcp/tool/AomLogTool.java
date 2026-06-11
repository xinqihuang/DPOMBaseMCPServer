/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.monitoring.aom.AomLogService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code query_logs} 能力的 MCP 工具：查询 AOM 应用、主机或自定义日志。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class AomLogTool {

    private final AomLogService service;

    /**
     * 构造 {@code AomLogTool} 实例。
     *
     * @param service AOM 日志查询服务
     */
    public AomLogTool(AomLogService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：根据时间区间、关键字、日志类型查询日志。
     *
     * @param category   日志类型：{@code app_log/node_log/custom_log}
     * @param startTime  起始时间（UTC 毫秒）
     * @param endTime    结束时间（UTC 毫秒）
     * @param keyWord    关键字搜索，可选
     * @param pageSize   单页大小，可选（默认 100）
     * @param isDesc     是否倒序，可选（默认 true）
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "query_logs",
            description = """
                    Query AOM (Application Operations Management) log records over a time \
                    window. Useful when investigating an incident — find recent error log lines, \
                    stack traces, or specific keywords from application, node, or custom logs in \
                    Huawei Cloud. 'category' selects the log source. 'start_time'/'end_time' are \
                    UTC milliseconds. 'key_word' supports exact, wildcard ('*ERR*'), phrase \
                    search, and '&&'/'||' boolean combinators. Returns the raw upstream 'result' \
                    JSON string for downstream parsing.""")
    public Object queryLogs(
            @ToolParam(description = "Log category: app_log / node_log / custom_log.")
            String category,
            @ToolParam(description = "Start timestamp (UTC millis).")
            Long startTime,
            @ToolParam(description = "End timestamp (UTC millis), must be > start_time.")
            Long endTime,
            @ToolParam(description = "Keyword filter (supports wildcard '*' and '&&'/'||').",
                    required = false)
            String keyWord,
            @ToolParam(description = "Page size [1, 1000], default 100.", required = false)
            Integer pageSize,
            @ToolParam(description = "Descending order by lineNum (newest first). Default true.",
                    required = false)
            Boolean isDesc) {

        AomQueryLogsRequest req = new AomQueryLogsRequest(
                category, startTime, endTime, keyWord, pageSize, isDesc);
        return ToolCallSupport.execute("query_logs", () -> service.queryLogs(req));
    }
}
