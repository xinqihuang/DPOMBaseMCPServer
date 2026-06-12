/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.monitoring.apm.ApmTrendService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code show_apm_trend} 能力的 MCP 工具。
 *
 * <p>按 {@code monitor_item_id × view_config × 时间窗} 拉取 APM 监控项趋势数据
 * （折线 / 汇总 / 明细表）。{@code view_config} 作为嵌套对象暴露给 Agent，Spring AI
 * 会从 record 反射出 JSON Schema；Tool 仅做请求装配与异常转换，业务校验下沉到
 * {@link ApmTrendService}。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmTrendTool implements McpTool {

    private final ApmTrendService service;

    /**
     * 构造 {@code ApmTrendTool} 实例。
     *
     * @param service APM 趋势查询服务
     */
    public ApmTrendTool(ApmTrendService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：拉取 APM 监控项趋势图数据。
     *
     * @param businessId    APM 业务 id（HTTP {@code x-business-id} 头）；为 {@code null} 时回落到配置
     * @param instanceId    APM 实例 id，可选
     * @param monitorItemId 监控项 id（核心过滤维度），可选
     * @param envId         环境 id，可选
     * @param startTime     开始时间字符串，必填
     * @param endTime       结束时间字符串，必填
     * @param viewConfig    视图配置（嵌套对象，含 view_type/metric_set/...），必填
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "show_apm_trend",
            description = """
                    STEP 3 (FINAL) of the APM trend discovery chain. Fetch APM trend data \
                    (line points or aggregation table) for the given time window. \

                    REQUIRED CALL ORDER: \
                    (1) show_env_monitor_items(env_id) → resolve collector_id from the target \
                        monitor_item_id (collector_id is ENV-LOCAL; never hard-code). \
                    (2) show_apm_monitor_item_view_config(collector_id, env_id) → pick exactly \
                        one view from view_row_list[*].view_list[*]. \
                    (3) Call THIS tool: copy the chosen view's fields verbatim into view_config \
                        (collector_name / metric_set / view_type / field_item_list with \
                        function/as). NEVER invent collector_name, metric_set, or function \
                        from prior knowledge — they must come from STEP 2's response. \

                    Note: ViewBase.latest from STEP 2 is Boolean but TrendView.latest here is \
                    String; convert ("true"/"false") when copying. \

                    Returns line_list[*] with {time, value} points; value is loosely typed \
                    (number or string per SDK). start_time/end_time are strings forwarded to \
                    upstream as-is. Trend data is NOT cached (real-time).""")
    public Object showTrend(
            @ToolParam(description = "APM business id (HTTP x-business-id header). Falls back to "
                    + "huaweicloud.apm-business-id config when null.", required = false)
            Long businessId,
            @ToolParam(description = "APM instance id.", required = false)
            Long instanceId,
            @ToolParam(description = "Monitor item id — the primary filter dimension. Pass the "
                    + "monitor_item_id returned by list_apm_alarm_data when investigating an alarm.",
                    required = false)
            Long monitorItemId,
            @ToolParam(description = "Environment id.", required = false)
            Long envId,
            @ToolParam(description = "Window start time (string, forwarded to upstream as-is).")
            String startTime,
            @ToolParam(description = "Window end time (string, forwarded to upstream as-is).")
            String endTime,
            @ToolParam(description = "View config object. Required fields: view_type "
                    + "(trend/sumtable/rawtable) and metric_set. Optional: collector_name, title, "
                    + "table_direction (H/V), group_by, filter, field_item_list, span, "
                    + "span_field, order_by, latest.")
            ApmTrendViewConfig viewConfig) {

        ApmTrendRequest req = new ApmTrendRequest(
                businessId, viewConfig, instanceId, monitorItemId, envId, startTime, endTime);
        return ToolCallSupport.execute("show_apm_trend", () -> service.showTrend(req));
    }
}
