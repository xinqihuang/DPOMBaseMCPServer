/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.lts.LtsDiscoveryService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 提供 LTS 日志组和日志流的只读发现工具。
 *
 * @author OpenAI
 * @since 2026-08-15
 */
@Component
public class LtsDiscoveryTool implements McpTool {

    private final LtsDiscoveryService service;

    /**
     * 创建 LTS 发现工具。
     *
     * @param service LTS 发现服务
     */
    public LtsDiscoveryTool(LtsDiscoveryService service) {
        this.service = service;
    }

    /**
     * 列出当前项目可访问的日志组。
     *
     * @return 日志组列表或统一错误响应
     */
    @Tool(name = "list_lts_log_groups", description = "List Huawei Cloud LTS log groups available in the "
            + "configured project. Read-only discovery step before list_lts_log_streams and query_lts_logs.")
    public Object listLogGroups() {
        return ToolCallSupport.execute("list_lts_log_groups", service::listLogGroups);
    }

    /**
     * 按可选名称条件列出日志流。
     *
     * @param logGroupName 日志组名称过滤条件
     * @param logStreamName 日志流名称过滤条件
     * @return 日志流列表或统一错误响应
     */
    @Tool(name = "list_lts_log_streams", description = "List Huawei Cloud LTS log streams with their real "
            + "log_group_id and log_stream_id. Read-only. Copy returned ids into query_lts_logs; do not invent ids.")
    public Object listLogStreams(
            @ToolParam(description = "Optional exact log group name filter.", required = false)
            String logGroupName,
            @ToolParam(description = "Optional exact log stream name filter.", required = false)
            String logStreamName) {
        return ToolCallSupport.execute("list_lts_log_streams",
                () -> service.listLogStreams(logGroupName, logStreamName));
    }
}
