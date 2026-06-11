/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code show_apm_monitor_item_view_config} 能力的 MCP 工具——T23 三步发现链第 2 步。
 *
 * <p>返回某采集器在指定 env 下的真实视图清单（{@code metric_set} / {@code field_item_list[*].function}
 * 等字面量来自上游）。Agent 据此挑选某个 view，原样填进 {@code show_apm_trend} 的
 * {@code viewConfig} 入参，从而避免凭模型先验编造 metric / function。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmMonitorItemViewConfigTool {

    private final ApmDiscoveryService service;

    /**
     * 构造 {@code ApmMonitorItemViewConfigTool} 实例。
     *
     * @param service APM 发现服务
     */
    public ApmMonitorItemViewConfigTool(ApmDiscoveryService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：拉取某采集器在指定 env 下的视图清单。
     *
     * @param collectorId 采集器 id（必填；来自 show_env_monitor_items 返回的 collector_id）
     * @param envId       环境 id（必填）
     * @param businessId  APM 业务 id；为 {@code null} 时回落到配置默认值
     * @return 成功时返回
     *         {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "show_apm_monitor_item_view_config",
            description = """
                    STEP 2 of the APM trend discovery chain. Returns the upstream-defined view \
                    list for a given (collector_id, env_id): view_row_list[*].view_list[*] \
                    where each view carries the real metric_set, view_type, and \
                    field_item_list[*].function / as / unit. \

                    Pick exactly ONE view from the response and pass its fields verbatim into \
                    show_apm_trend.view_config — do NOT invent collector_name / metric_set / \
                    function from prior knowledge. \

                    Inputs MUST come from STEP 1 (show_env_monitor_items): take collector_id \
                    from monitor_item_info_list[*].collector_id. collector_id is env-local — \
                    never reuse across envs.""")
    public Object showMonitorItemViewConfig(
            @ToolParam(description = "Collector id (required). Take it from "
                    + "show_env_monitor_items.monitor_item_info_list[*].collector_id.")
            Long collectorId,
            @ToolParam(description = "Environment id (required).")
            Long envId,
            @ToolParam(description = "APM business id (HTTP x-business-id header). Falls back to "
                    + "huaweicloud.apm-business-id config when null.", required = false)
            Long businessId) {
        return ToolCallSupport.execute("show_apm_monitor_item_view_config",
                () -> service.getMonitorItemViewConfig(collectorId, envId, businessId));
    }
}
