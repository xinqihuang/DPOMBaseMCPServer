/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
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
 * 向 Spring AI MCP Server 注册 MCP 工具 Bean。
 *
 * <p>新增工具组件时，请将其加入 {@code toolObjects(...)} 列表。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class McpServerConfig {

    /**
     * 将所有 MCP 工具组件 Bean 聚合为单个 {@link ToolCallbackProvider}，
     * 供 Spring AI MCP Server 进行工具发现与调用。
     *
     * @param helloWorldTool 用于连通性测试的简易工具
     * @param cesMetricsTool CES 指标列表查询工具
     * @param aomMetricsTool AOM 指标列表查询工具
     * @return 暴露所有已注册工具对象的 {@link MethodToolCallbackProvider}
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
