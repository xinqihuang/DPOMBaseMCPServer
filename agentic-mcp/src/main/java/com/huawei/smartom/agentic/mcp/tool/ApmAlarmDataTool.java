/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmAlarmService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code list_apm_alarm_data} 能力的 MCP 工具。
 *
 * <p>按 14 个上游过滤参数 + 2 个分页参数 + business_id 头查询华为云 APM 告警记录；
 * 入参全部下沉到 service 校验，Tool 仅做请求装配 + 异常转换。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmAlarmDataTool {

    private final ApmAlarmService service;

    /**
     * 构造 {@code ApmAlarmDataTool} 实例。
     *
     * @param service APM 告警查询服务
     */
    public ApmAlarmDataTool(ApmAlarmService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：列出 APM 告警记录。
     *
     * @param businessId        APM 业务 id（HTTP {@code x-business-id} 头）；为 null 时回落到配置默认值
     * @param page              页码 1 起始；可为 null（按上游默认）
     * @param pageSize          单页大小 [1, 100]；可为 null
     * @param region            资源 region 过滤
     * @param appName           应用名过滤
     * @param businessIdFilter  业务 id 过滤（body 中的 business_id）
     * @param monitorItemId     监控项 id 过滤
     * @param status            告警状态过滤
     * @param alarmLevel        告警级别字符串过滤
     * @param keyword           关键字模糊匹配
     * @param alarmStartTime    告警起始时间字符串
     * @param alarmEndTime      告警截止时间字符串
     * @param collectorId       采集器 id 过滤
     * @param ipAddress         实例 IP 过滤
     * @param envList           环境 id 列表过滤
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "list_apm_alarm_data",
            description = """
                    List APM (Application Performance Management) alarm records for Huawei Cloud \
                    applications. Filter by time window (alarm_start_time / alarm_end_time as \
                    upstream-format string), app_name, status, severity (alarm_level), keyword, \
                    instance IP, env id list, monitor item, collector. Returns full alarm \
                    metadata: rule identity, severity, instance / IP / region, lifecycle \
                    timestamps (alarm_first_time / alarm_last_time / gmt_create / gmt_modify), \
                    and the alarm expression. Two business_id concepts coexist: 'business_id' is \
                    the HTTP x-business-id header (tenant context, falls back to \
                    huaweicloud.apm-business-id config when null); 'business_id_filter' is an \
                    optional body filter. Use the returned 'id' with list_alarm_notify to fetch \
                    delivery records for a specific alarm.""")
    public Object listApmAlarmData(
            @ToolParam(description = "APM business id (HTTP x-business-id header). Falls back to "
                    + "huaweicloud.apm-business-id config when null.", required = false)
            Long businessId,
            @ToolParam(description = "Page number, 1-indexed.", required = false)
            Integer page,
            @ToolParam(description = "Page size in [1, 100].", required = false)
            Integer pageSize,
            @ToolParam(description = "Resource region filter (e.g. cn-north-4).", required = false)
            String region,
            @ToolParam(description = "Application name filter.", required = false)
            String appName,
            @ToolParam(description = "Body-level business_id filter (independent from the header "
                    + "x-business-id).", required = false)
            Long businessIdFilter,
            @ToolParam(description = "Monitor item id filter.", required = false)
            Long monitorItemId,
            @ToolParam(description = "Alarm status filter (upstream-defined; passed through).",
                       required = false)
            String status,
            @ToolParam(description = "Alarm level as a string (upstream-defined).", required = false)
            String alarmLevel,
            @ToolParam(description = "Free-text keyword filter.", required = false)
            String keyword,
            @ToolParam(description = "Alarm start time string (upstream format; passed through).",
                       required = false)
            String alarmStartTime,
            @ToolParam(description = "Alarm end time string (upstream format).", required = false)
            String alarmEndTime,
            @ToolParam(description = "Collector id filter.", required = false)
            Integer collectorId,
            @ToolParam(description = "Instance IP filter.", required = false)
            String ipAddress,
            @ToolParam(description = "Env id list filter (List<Long>).", required = false)
            List<Long> envList) {

        ApmAlarmDataRequest req = new ApmAlarmDataRequest(
                businessId, page, pageSize, region, appName, businessIdFilter,
                monitorItemId, status, alarmLevel, keyword, alarmStartTime, alarmEndTime,
                collectorId, ipAddress, envList);
        return ToolCallSupport.execute("list_apm_alarm_data", () -> service.listAlarmData(req));
    }
}
