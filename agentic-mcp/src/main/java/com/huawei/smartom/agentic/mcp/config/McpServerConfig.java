/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.mcp.config;

import com.huawei.smartom.agentic.mcp.tool.AomMetricsTool;
import com.huawei.smartom.agentic.mcp.tool.CesMetricsTool;
import com.huawei.smartom.agentic.mcp.tool.HelloWorldTool;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers MCP tool beans with the Spring AI MCP server.
 *
 * <p>Add new tool components to the {@code toolObjects(...)} list as they are introduced.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class McpServerConfig {

    /**
     * Aggregates all MCP tool component beans into a single {@link ToolCallbackProvider}
     * consumed by the Spring AI MCP server for tool discovery and invocation.
     *
     * @param helloWorldTool the trivial reachability test tool
     * @param cesMetricsTool the CES metrics listing tool
     * @param aomMetricsTool the AOM metrics listing tool
     * @return a {@link MethodToolCallbackProvider} exposing every registered tool object
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            HelloWorldTool helloWorldTool,
            CesMetricsTool cesMetricsTool,
            AomMetricsTool aomMetricsTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(helloWorldTool, cesMetricsTool, aomMetricsTool)
                .build();
    }
}
