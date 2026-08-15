/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.discovery.DiscoveryRequest;
import com.huawei.smartom.agentic.monitoring.discovery.ResourceDiscoveryService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 统一资源发现 MCP 工具：把受控锚点规范化为带 provenance/ambiguity 的 ResourceContext 或候选列表。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Component
public class UnifiedResourceDiscoveryTool implements McpTool {

    private final ResourceDiscoveryService service;

    /**
     * 构造统一资源发现工具。
     *
     * @param service 资源发现编排
     */
    public UnifiedResourceDiscoveryTool(ResourceDiscoveryService service) {
        this.service = service;
    }

    /**
     * 返回规范化 ResourceContext。
     *
     * @param region        区域
     * @param serviceName   服务名
     * @param appName       APM 组件名
     * @param clusterId     CCE 集群 id
     * @param namespace     命名空间
     * @param podName       Pod 名
     * @param workloadName  工作负载名
     * @param businessId    APM 业务 id
     * @param envId         APM 环境 id
     * @param instanceId    APM 实例 id
     * @param monitorItemId APM 监控项 id
     * @param ipAddress     实例 IP
     * @param alarmId       APM 告警记录 id
     * @param traceId       APM trace id
     * @param logGroupId    LTS 日志组 id
     * @param logGroupName  LTS 日志组名
     * @param logStreamId   LTS 日志流 id
     * @param logStreamName LTS 日志流名
     * @return ResourceContext 或 ErrorResponse
     */
    @Tool(name = "discover_resource_context", description = "Normalize known anchors into a read-only "
            + "ResourceContext with per-field provenance and ambiguity. Pass at least one anchor "
            + "(region/service/app/clusterId/namespace/pod/workload/APM business/env/instance/monitorItem/IP/"
            + "alarm/trace/LTS group/stream). Reuses existing read-only discovery tools; unresolved "
            + "mappings are reported as missing capabilities, never guessed.")
    public Object discoverResourceContext(
            @ToolParam(description = "Region, e.g. cn-north-9.", required = false) String region,
            @ToolParam(description = "Service name.", required = false) String serviceName,
            @ToolParam(description = "APM app name.", required = false) String appName,
            @ToolParam(description = "CCE cluster id.", required = false) String clusterId,
            @ToolParam(description = "Kubernetes namespace.", required = false) String namespace,
            @ToolParam(description = "Pod name.", required = false) String podName,
            @ToolParam(description = "Workload name.", required = false) String workloadName,
            @ToolParam(description = "APM business id from list_apm_business.", required = false) Long businessId,
            @ToolParam(description = "APM env id.", required = false) Long envId,
            @ToolParam(description = "APM instance id.", required = false) Long instanceId,
            @ToolParam(description = "APM monitor item id.", required = false) Long monitorItemId,
            @ToolParam(description = "Instance IP.", required = false) String ipAddress,
            @ToolParam(description = "APM alarm record id.", required = false) String alarmId,
            @ToolParam(description = "APM trace id.", required = false) String traceId,
            @ToolParam(description = "LTS log group id.", required = false) String logGroupId,
            @ToolParam(description = "LTS log group name.", required = false) String logGroupName,
            @ToolParam(description = "LTS log stream id.", required = false) String logStreamId,
            @ToolParam(description = "LTS log stream name.", required = false) String logStreamName) {
        return ToolCallSupport.execute("discover_resource_context", () -> service.discover(
                new DiscoveryRequest(region, serviceName, appName, clusterId, namespace, podName, workloadName,
                        businessId, envId, instanceId, monitorItemId, ipAddress, alarmId, traceId, logGroupId,
                        logGroupName, logStreamId, logStreamName)));
    }

    /**
     * 返回无法唯一映射的候选列表。
     *
     * @param region        区域
     * @param serviceName   服务名
     * @param appName       APM 组件名
     * @param clusterId     CCE 集群 id
     * @param namespace     命名空间
     * @param podName       Pod 名
     * @param workloadName  工作负载名
     * @param businessId    APM 业务 id
     * @param envId         APM 环境 id
     * @param instanceId    APM 实例 id
     * @param monitorItemId APM 监控项 id
     * @param ipAddress     实例 IP
     * @param alarmId       APM 告警记录 id
     * @param traceId       APM trace id
     * @param logGroupId    LTS 日志组 id
     * @param logGroupName  LTS 日志组名
     * @param logStreamId   LTS 日志流 id
     * @param logStreamName LTS 日志流名
     * @return 候选列表或 ErrorResponse
     */
    @Tool(name = "resolve_resource_candidates", description = "Resolve resource identifier candidates "
            + "when a mapping is ambiguous. Returns candidates with match type, source and the next "
            + "step to disambiguate; never silently picks the first candidate.")
    public Object resolveResourceCandidates(
            @ToolParam(description = "Region, e.g. cn-north-9.", required = false) String region,
            @ToolParam(description = "Service name.", required = false) String serviceName,
            @ToolParam(description = "APM app name.", required = false) String appName,
            @ToolParam(description = "CCE cluster id.", required = false) String clusterId,
            @ToolParam(description = "Kubernetes namespace.", required = false) String namespace,
            @ToolParam(description = "Pod name.", required = false) String podName,
            @ToolParam(description = "Workload name.", required = false) String workloadName,
            @ToolParam(description = "APM business id from list_apm_business.", required = false) Long businessId,
            @ToolParam(description = "APM env id.", required = false) Long envId,
            @ToolParam(description = "APM instance id.", required = false) Long instanceId,
            @ToolParam(description = "APM monitor item id.", required = false) Long monitorItemId,
            @ToolParam(description = "Instance IP.", required = false) String ipAddress,
            @ToolParam(description = "APM alarm record id.", required = false) String alarmId,
            @ToolParam(description = "APM trace id.", required = false) String traceId,
            @ToolParam(description = "LTS log group id.", required = false) String logGroupId,
            @ToolParam(description = "LTS log group name.", required = false) String logGroupName,
            @ToolParam(description = "LTS log stream id.", required = false) String logStreamId,
            @ToolParam(description = "LTS log stream name.", required = false) String logStreamName) {
        return ToolCallSupport.execute("resolve_resource_candidates", () -> service.resolve(
                new DiscoveryRequest(region, serviceName, appName, clusterId, namespace, podName, workloadName,
                        businessId, envId, instanceId, monitorItemId, ipAddress, alarmId, traceId, logGroupId,
                        logGroupName, logStreamId, logStreamName)));
    }
}
