/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.monitoring.lts.LtsLogService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 封装 {@code query_lts_logs} 能力的 MCP 工具。
 *
 * <p>用于按时间区间 / 关键字 / 标签 / SQL 查询华为云 LTS 日志流的原始日志条目。
 * 入参全部下沉到 service 校验，Tool 仅做请求装配 + 异常转换。
 *
 * @author h00884391
 * @since 2026-06-03
 */
@Component
public class LtsLogTool {

    private final LtsLogService service;

    /**
     * 构造 {@code LtsLogTool} 实例。
     *
     * @param service LTS 日志检索服务
     */
    public LtsLogTool(LtsLogService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按时间区间 / 关键字 / 标签 / SQL 查询 LTS 日志。
     *
     * @param logGroupId       LTS 日志组 id（必填）
     * @param logStreamId      LTS 日志流 id（必填）
     * @param startTimeMillis  搜索起始时间（UTC 毫秒），可选
     * @param endTimeMillis    搜索结束时间（UTC 毫秒），可选
     * @param labels           标签过滤，AND 关系，可选
     * @param keywords         关键字过滤，可选
     * @param query            SQL / 表达式查询，可选
     * @param isAnalysisQuery  {@code true} 时启用 SQL 分析模式（结果走 {@code analysisLogs}），可选
     * @param isCount          {@code true} 时只返回命中条数，可选
     * @param limit            单页大小 [1, 5000]，可选
     * @param isDesc           是否倒序，可选
     * @param highlight        是否高亮关键字，可选
     * @param isIterative      是否启用迭代查询，可选
     * @param searchType       分页方向：{@code forwards} / {@code backwards}，可选
     * @param lineNum          游标分页：上次结果尾行的 line_num，可选；必须与 {@code cursorTime} 成对
     * @param cursorTime       游标分页：与 {@code lineNum} 配对的 {@code __time__} 字符串，可选
     * @param scrollId         scroll API 分页 id，可选
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "query_lts_logs",
            description = """
                    Search raw logs from a Huawei Cloud LTS (Log Tank Service) log stream by \
                    time range, keyword, labels, or SQL. Use this when investigating an incident \
                    to locate ERROR/Exception/specific log entries within a known log_group_id + \
                    log_stream_id. Returns log content + line_num cursor so the agent can call \
                    query_lts_log_context to fetch surrounding entries. Set is_analysis_query=true \
                    with a SQL 'query' to run structured analysis (top-N / counts); results then \
                    come back via analysis_logs. 'start_time_millis' / 'end_time_millis' are UTC \
                    millis; pagination is cursor-based via line_num + cursor_time, or scroll_id.""")
    public Object queryLtsLogs(
            @ToolParam(description = "LTS log group id (required).")
            String logGroupId,
            @ToolParam(description = "LTS log stream id (required).")
            String logStreamId,
            @ToolParam(description = "Start time, UTC millis.", required = false)
            Long startTimeMillis,
            @ToolParam(description = "End time, UTC millis. Must be > start_time_millis.",
                       required = false)
            Long endTimeMillis,
            @ToolParam(description = "Label filter as a JSON object {key: value}; entries are "
                    + "AND-combined.", required = false)
            Map<String, String> labels,
            @ToolParam(description = "Free-text keyword filter.", required = false)
            String keywords,
            @ToolParam(description = "SQL or expression query. Set is_analysis_query=true to "
                    + "evaluate as SQL; results then return via analysis_logs.", required = false)
            String query,
            @ToolParam(description = "Set true to run 'query' as SQL analysis.", required = false)
            Boolean isAnalysisQuery,
            @ToolParam(description = "If true, only return matched row count.", required = false)
            Boolean isCount,
            @ToolParam(description = "Page size in [1, 5000].", required = false)
            Integer limit,
            @ToolParam(description = "Sort direction: true for desc.", required = false)
            Boolean isDesc,
            @ToolParam(description = "Highlight matched keywords in returned content.",
                       required = false)
            Boolean highlight,
            @ToolParam(description = "Iterative query mode.", required = false)
            Boolean isIterative,
            @ToolParam(description = "Pagination direction: 'forwards' or 'backwards'. Leave "
                    + "null for first page.", required = false)
            String searchType,
            @ToolParam(description = "Cursor pagination: line_num from previous page tail. Must "
                    + "pair with cursor_time.", required = false)
            String lineNum,
            @ToolParam(description = "Cursor pagination: __time__ from previous page tail. Must "
                    + "pair with line_num.", required = false)
            String cursorTime,
            @ToolParam(description = "Scroll-API id for an alternative pagination mode.",
                       required = false)
            String scrollId) {

        LtsListLogsRequest req = new LtsListLogsRequest(
                logGroupId, logStreamId, startTimeMillis, endTimeMillis,
                labels, keywords, query, isAnalysisQuery, isCount, limit,
                isDesc, highlight, isIterative, searchType,
                lineNum, cursorTime, scrollId);
        return ToolCallSupport.execute("query_lts_logs", () -> service.queryLogs(req));
    }
}
