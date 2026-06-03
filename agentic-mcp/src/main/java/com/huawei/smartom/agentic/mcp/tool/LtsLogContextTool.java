/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.lts.LtsLogContextService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code query_lts_log_context} 能力的 MCP 工具。
 *
 * <p>用于在 {@code query_lts_logs} 命中目标日志后，按行号游标拉取该日志前后 N 条上下文。
 * Tool 仅做请求装配与异常转换，业务校验下沉到 {@link LtsLogContextService}。
 *
 * @author h00884391
 * @since 2026-06-03
 */
@Component
public class LtsLogContextTool {

    private static final Logger LOG = LoggerFactory.getLogger(LtsLogContextTool.class);

    private final LtsLogContextService service;

    /**
     * 构造 {@code LtsLogContextTool} 实例。
     *
     * @param service LTS 日志上下文查询服务
     */
    public LtsLogContextTool(LtsLogContextService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按目标日志游标拉取前后上下文。
     *
     * @param logGroupId    LTS 日志组 id（必填）
     * @param logStreamId   LTS 日志流 id（必填）
     * @param lineNum       目标日志 line_num；首次调用必填，与 {@code cursorTime} 成对
     * @param cursorTime    目标日志 __time__；首次调用必填，与 {@code lineNum} 成对
     * @param backwardsSize 向前取多少条，[0, 500]；可选，SDK 默认 100
     * @param forwardsSize  向后取多少条，[0, 500]；可选，SDK 默认 100
     * @param scrollId      scroll 续翻页 id；提供时可省略 line_num / cursor_time
     * @return 成功时返回
     *         {@link com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse}，
     *         失败时返回 {@link ErrorResponse}
     */
    @Tool(
            name = "query_lts_log_context",
            description = """
                    Fetch surrounding log entries for a specific target log line in a Huawei Cloud \
                    LTS log stream. Use this after query_lts_logs identifies a line of interest — \
                    pass the target's line_num and cursor_time (from the previous response) plus \
                    backwards_size / forwards_size to retrieve N entries before and after it. \
                    Useful for reconstructing event sequence around an incident. For follow-up \
                    pages from a single first call, pass only scroll_id from the previous \
                    response.""")
    public Object queryLtsLogContext(
            @ToolParam(description = "LTS log group id (required).")
            String logGroupId,
            @ToolParam(description = "LTS log stream id (required).")
            String logStreamId,
            @ToolParam(description = "Target log line_num. Required for first call; pair with "
                    + "cursor_time.", required = false)
            String lineNum,
            @ToolParam(description = "Target log __time__. Required for first call; pair with "
                    + "line_num.", required = false)
            String cursorTime,
            @ToolParam(description = "Number of entries to fetch before the target, in [0, 500]. "
                    + "SDK default 100.", required = false)
            Integer backwardsSize,
            @ToolParam(description = "Number of entries to fetch after the target, in [0, 500]. "
                    + "SDK default 100.", required = false)
            Integer forwardsSize,
            @ToolParam(description = "Scroll id from a previous response; provide alone to fetch "
                    + "the next page.", required = false)
            String scrollId) {

        LtsListLogContextRequest req = new LtsListLogContextRequest(
                logGroupId, logStreamId, lineNum, cursorTime,
                backwardsSize, forwardsSize, scrollId);
        try {
            return service.queryContext(req);
        }
        catch (SmartomException e) {
            LOG.warn("query_lts_log_context failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
