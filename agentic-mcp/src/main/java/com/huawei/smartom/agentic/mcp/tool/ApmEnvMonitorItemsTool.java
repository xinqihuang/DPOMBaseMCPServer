/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code show_env_monitor_items} 能力的 MCP 工具——T23 三步发现链的入口。
 *
 * <p>解决核心问题：{@code collector_id} 是 env 局部的（同一数值在不同 env 含义不同），
 * Agent 必须运行时通过本工具获取 {@code monitor_item↔collector_id} 的映射表，禁止
 * 跨 env 复用或硬编码。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmEnvMonitorItemsTool {

    private final ApmDiscoveryService service;

    /**
     * 构造 {@code ApmEnvMonitorItemsTool} 实例。
     *
     * @param service APM 发现服务
     */
    public ApmEnvMonitorItemsTool(ApmDiscoveryService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：拉取 env 维度的监控项 + 采集器类别列表。
     *
     * @param envId      环境 id（必填）
     * @param businessId APM 业务 id；为 {@code null} 时回落到配置默认值
     * @return 成功时返回
     *         {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "show_env_monitor_items",
            description = """
                    STEP 1 of the APM trend discovery chain. List all monitor items and \
                    collector categories belonging to the given env_id. Returns \
                    monitor_item_info_list[] where each entry carries (monitor_item_id, \
                    collector_id, collector_name, display_name, category_id, disabled, \
                    collect_interval). This is THE authoritative monitor_item ↔ collector_id \
                    mapping for that env. \

                    IMPORTANT: collector_id is env-local — the same numeric id means a \
                    different collector under different envs. NEVER hard-code collector_id or \
                    reuse it across envs; always re-resolve via this tool. \

                    Typical flow: alarm gives you a monitor_item_id → call this tool to find \
                    its collector_id (or pick a collector by display_name) → \
                    show_apm_monitor_item_view_config(collector_id, env_id) → \
                    show_apm_trend(view from that response).""")
    public Object showEnvMonitorItems(
            @ToolParam(description = "Environment id (required).")
            Long envId,
            @ToolParam(description = "APM business id (HTTP x-business-id header). Falls back to "
                    + "huaweicloud.apm-business-id config when null.", required = false)
            Long businessId) {
        return ToolCallSupport.execute("show_env_monitor_items", () -> service.getEnvMonitorItems(envId, businessId));
    }
}
