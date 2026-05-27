/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.config;

import com.huawei.smartom.agentic.mcp.tool.AomLogTool;
import com.huawei.smartom.agentic.mcp.tool.AomMetricDataTool;
import com.huawei.smartom.agentic.mcp.tool.AomMetricsTool;
import com.huawei.smartom.agentic.mcp.tool.ApmTopologyTool;
import com.huawei.smartom.agentic.mcp.tool.ApmTraceTool;
import com.huawei.smartom.agentic.mcp.tool.CesAlarmTool;
import com.huawei.smartom.agentic.mcp.tool.CesMetricDataTool;
import com.huawei.smartom.agentic.mcp.tool.CesMetricsTool;
import com.huawei.smartom.agentic.mcp.tool.CorrelateIncidentTool;
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
     * @param helloWorldTool       连通性测试工具
     * @param cesMetricsTool       CES 指标定义查询工具
     * @param cesMetricDataTool    CES 指标数据查询工具（T07）
     * @param cesAlarmTool         CES 告警历史查询工具（T09）
     * @param aomMetricsTool       AOM 指标定义查询工具
     * @param aomMetricDataTool    AOM 时序数据查询工具（T08）
     * @param aomLogTool           AOM 日志查询工具（T13）
     * @param apmTraceTool         APM trace 搜索工具（T10）
     * @param apmTopologyTool      APM 拓扑查询工具（T11）
     * @param correlateIncidentTool 跨组件事故关联工具（T12）
     * @return 暴露所有已注册工具对象的 {@link MethodToolCallbackProvider}
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            HelloWorldTool helloWorldTool,
            CesMetricsTool cesMetricsTool,
            CesMetricDataTool cesMetricDataTool,
            CesAlarmTool cesAlarmTool,
            AomMetricsTool aomMetricsTool,
            AomMetricDataTool aomMetricDataTool,
            AomLogTool aomLogTool,
            ApmTraceTool apmTraceTool,
            ApmTopologyTool apmTopologyTool,
            CorrelateIncidentTool correlateIncidentTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        helloWorldTool,
                        cesMetricsTool,
                        cesMetricDataTool,
                        cesAlarmTool,
                        aomMetricsTool,
                        aomMetricDataTool,
                        aomLogTool,
                        apmTraceTool,
                        apmTopologyTool,
                        correlateIncidentTool)
                .build();
    }
}
