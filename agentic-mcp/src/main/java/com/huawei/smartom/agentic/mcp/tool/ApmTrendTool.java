/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.apm.ApmTrendService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class ApmTrendTool {

    private static final Logger LOG = LoggerFactory.getLogger(ApmTrendTool.class);

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
     *         失败时返回 {@link ErrorResponse}
     */
    @Tool(
            name = "show_apm_trend",
            description = """
                    Fetch APM monitor-item trend data (line points or aggregation table) for a \
                    given time window. Use this AFTER you identify a monitor_item_id (e.g. from \
                    an APM alarm's monitor_item_id returned by list_apm_alarm_data, or from a \
                    known APM application's monitor config). The result contains one or more \
                    lines, each with a list of {time, value} points; value is loosely typed \
                    (number or string per SDK). Pair with list_apm_alarm_data when you need to \
                    inspect the metric behind an alarm. start_time/end_time are forwarded to \
                    upstream as-is (strings).""")
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
        try {
            return service.showTrend(req);
        }
        catch (SmartomException e) {
            LOG.warn("show_apm_trend failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
