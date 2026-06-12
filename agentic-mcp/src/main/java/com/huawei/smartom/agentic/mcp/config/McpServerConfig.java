/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.config;

import com.huawei.smartom.agentic.mcp.tool.McpTool;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 向 Spring AI MCP Server 注册 MCP 工具 Bean。
 *
 * <p>所有工具通过实现 {@link McpTool} 标记接口被 Spring 自动收集，新增工具时只需
 * 让工具类标注 {@code @Component} 并实现 {@link McpTool}，本配置类无需改动。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class McpServerConfig {

    /**
     * 收集容器内所有 {@link McpTool} 实现，聚合为单个 {@link ToolCallbackProvider}，
     * 供 Spring AI MCP Server 进行工具发现与调用。
     *
     * <p>注入顺序如对工具暴露有意义，可在工具类上标注 {@code @Order} 控制。
     *
     * @param mcpTools Spring 自动注入的全部 {@link McpTool} 实现，不会为 {@code null}
     * @return 暴露所有已注册工具对象的 {@link MethodToolCallbackProvider}
     * @throws IllegalStateException 当未发现任何工具时抛出，用于尽早暴露包扫描配置错误
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(List<McpTool> mcpTools) {
        if (mcpTools.isEmpty()) {
            throw new IllegalStateException("未发现任何 McpTool 实现，请检查工具组件的 @Component 与包扫描配置");
        }
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools.toArray())
                .build();
    }
}
