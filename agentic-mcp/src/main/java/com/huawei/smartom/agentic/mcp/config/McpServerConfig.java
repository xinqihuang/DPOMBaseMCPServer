/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.config;

import com.huawei.smartom.agentic.mcp.tool.AomEventTool;
import com.huawei.smartom.agentic.mcp.tool.AomLogTool;
import com.huawei.smartom.agentic.mcp.tool.AomMetricDataTool;
import com.huawei.smartom.agentic.mcp.tool.AomMetricsTool;
import com.huawei.smartom.agentic.mcp.tool.ApmAlarmDataTool;
import com.huawei.smartom.agentic.mcp.tool.ApmAlarmNotifyTool;
import com.huawei.smartom.agentic.mcp.tool.ApmApplicationTool;
import com.huawei.smartom.agentic.mcp.tool.ApmBusinessTool;
import com.huawei.smartom.agentic.mcp.tool.ApmClobDetailTool;
import com.huawei.smartom.agentic.mcp.tool.ApmEventDetailTool;
import com.huawei.smartom.agentic.mcp.tool.ApmEnvMonitorItemsTool;
import com.huawei.smartom.agentic.mcp.tool.ApmMonitorItemViewConfigTool;
import com.huawei.smartom.agentic.mcp.tool.ApmTopologyTool;
import com.huawei.smartom.agentic.mcp.tool.ApmTraceEventsTool;
import com.huawei.smartom.agentic.mcp.tool.ApmTraceTool;
import com.huawei.smartom.agentic.mcp.tool.ApmTrendTool;
import com.huawei.smartom.agentic.mcp.tool.CesAlarmTool;
import com.huawei.smartom.agentic.mcp.tool.CesBatchMetricDataTool;
import com.huawei.smartom.agentic.mcp.tool.CesCreateNotificationMaskTool;
import com.huawei.smartom.agentic.mcp.tool.CesDeleteNotificationMasksTool;
import com.huawei.smartom.agentic.mcp.tool.CesListNotificationMasksTool;
import com.huawei.smartom.agentic.mcp.tool.CesMetricDataTool;
import com.huawei.smartom.agentic.mcp.tool.CesMetricsTool;
import com.huawei.smartom.agentic.mcp.tool.CorrelateIncidentTool;
import com.huawei.smartom.agentic.mcp.tool.HelloWorldTool;
import com.huawei.smartom.agentic.mcp.tool.LtsLogContextTool;
import com.huawei.smartom.agentic.mcp.tool.LtsLogTool;

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
     * @param helloWorldTool                  连通性测试工具
     * @param cesMetricsTool                  CES 指标定义查询工具
     * @param cesMetricDataTool               CES 指标数据查询工具（T07）
     * @param cesBatchMetricDataTool          CES 批量指标数据查询工具
     * @param cesAlarmTool                    CES 告警历史查询工具（T09）
     * @param aomMetricsTool                  AOM 指标定义查询工具
     * @param aomMetricDataTool               AOM 时序数据查询工具（T08）
     * @param aomLogTool                      AOM 日志查询工具（T13）
     * @param aomEventTool                    AOM 事件/告警查询工具（T27）
     * @param apmTraceTool                    APM trace 搜索工具（T10）
     * @param apmTopologyTool                 APM 拓扑查询工具（T11）
     * @param correlateIncidentTool           跨组件事故关联工具（T12）
     * @param cesCreateNotificationMaskTool   创建告警屏蔽规则工具
     * @param cesDeleteNotificationMasksTool  批量删除告警屏蔽规则工具
     * @param cesListNotificationMasksTool    查询告警屏蔽规则工具
     * @param ltsLogTool                      LTS 日志检索工具（T17）
     * @param ltsLogContextTool               LTS 日志上下文工具（T18）
     * @param apmAlarmDataTool                APM 告警列表工具（T20）
     * @param apmAlarmNotifyTool              APM 告警通知投递工具（T21）
     * @param apmTrendTool                    APM 趋势图查询工具（T22）
     * @param apmBusinessTool                 APM 应用列表发现工具（T28 链路第 0 步）
     * @param apmApplicationTool              APM 组件/环境搜索工具（T28 链路第 1 步）
     * @param apmEnvMonitorItemsTool          APM env 监控项发现工具（T23 链路第 1 步）
     * @param apmMonitorItemViewConfigTool    APM 视图配置发现工具（T23 链路第 2 步）
     * @param apmTraceEventsTool              APM 调用链事件序列工具（T29 链路第 3 步）
     * @param apmEventDetailTool              APM 事件详情工具（T29 链路第 4 步）
     * @param apmClobDetailTool               APM clob 全文工具（T29 链路第 5 步）
     * @return 暴露所有已注册工具对象的 {@link MethodToolCallbackProvider}
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            HelloWorldTool helloWorldTool,
            CesMetricsTool cesMetricsTool,
            CesMetricDataTool cesMetricDataTool,
            CesBatchMetricDataTool cesBatchMetricDataTool,
            CesAlarmTool cesAlarmTool,
            AomMetricsTool aomMetricsTool,
            AomMetricDataTool aomMetricDataTool,
            AomLogTool aomLogTool,
            AomEventTool aomEventTool,
            ApmTraceTool apmTraceTool,
            ApmTopologyTool apmTopologyTool,
            CorrelateIncidentTool correlateIncidentTool,
            CesCreateNotificationMaskTool cesCreateNotificationMaskTool,
            CesDeleteNotificationMasksTool cesDeleteNotificationMasksTool,
            CesListNotificationMasksTool cesListNotificationMasksTool,
            LtsLogTool ltsLogTool,
            LtsLogContextTool ltsLogContextTool,
            ApmAlarmDataTool apmAlarmDataTool,
            ApmAlarmNotifyTool apmAlarmNotifyTool,
            ApmTrendTool apmTrendTool,
            ApmBusinessTool apmBusinessTool,
            ApmApplicationTool apmApplicationTool,
            ApmEnvMonitorItemsTool apmEnvMonitorItemsTool,
            ApmMonitorItemViewConfigTool apmMonitorItemViewConfigTool,
            ApmTraceEventsTool apmTraceEventsTool,
            ApmEventDetailTool apmEventDetailTool,
            ApmClobDetailTool apmClobDetailTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        helloWorldTool,
                        cesMetricsTool,
                        cesMetricDataTool,
                        cesBatchMetricDataTool,
                        cesAlarmTool,
                        aomMetricsTool,
                        aomMetricDataTool,
                        aomLogTool,
                        aomEventTool,
                        apmTraceTool,
                        apmTopologyTool,
                        correlateIncidentTool,
                        cesCreateNotificationMaskTool,
                        cesDeleteNotificationMasksTool,
                        cesListNotificationMasksTool,
                        ltsLogTool,
                        ltsLogContextTool,
                        apmAlarmDataTool,
                        apmAlarmNotifyTool,
                        apmTrendTool,
                        apmBusinessTool,
                        apmApplicationTool,
                        apmEnvMonitorItemsTool,
                        apmMonitorItemViewConfigTool,
                        apmTraceEventsTool,
                        apmEventDetailTool,
                        apmClobDetailTool)
                .build();
    }
}
