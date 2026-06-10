/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyRequest;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.apm.ApmAlarmService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code list_alarm_notify} 能力的 MCP 工具。
 *
 * <p>在 {@code list_apm_alarm_data} 命中告警后，按 {@code alarmDataId} 拉取该告警的通知
 * 投递记录。Tool 仅做请求装配与异常转换，业务校验下沉到 {@link ApmAlarmService}。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmAlarmNotifyTool {

    private static final Logger LOG = LoggerFactory.getLogger(ApmAlarmNotifyTool.class);

    private final ApmAlarmService service;

    /**
     * 构造 {@code ApmAlarmNotifyTool} 实例。
     *
     * @param service APM 告警查询服务
     */
    public ApmAlarmNotifyTool(ApmAlarmService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按告警 id 列出通知投递记录。
     *
     * @param businessId  APM 业务 id（HTTP x-business-id 头）；为 null 时回落到配置默认值
     * @param alarmDataId 告警记录 id（来自 list_apm_alarm_data 响应的 id），必填且 > 0
     * @param page        页码 1 起始；可选
     * @param pageSize    单页大小 [1, 100]；可选
     * @param region      资源 region 过滤
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyResponse}，
     *         失败时返回 {@link ErrorResponse}
     */
    @Tool(
            name = "list_alarm_notify",
            description = """
                    List notification delivery records for a specific APM alarm. Call this AFTER \
                    list_apm_alarm_data returns an alarm's id — pass that id as alarm_data_id \
                    here (note: SDK type is Integer; if the id returned from list_apm_alarm_data \
                    exceeds Integer.MAX_VALUE the upstream will reject). Returns notification \
                    metadata (notify_type such as sms/email/webhook, notify_status success or \
                    failure, alarm_content snapshot, alarm_rule_id, template_id, \
                    alarm_data_event_id, gmt_create). Use this to verify whether an alarm has \
                    actually been delivered and through which channels.""")
    public Object listAlarmNotify(
            @ToolParam(description = "APM business id (HTTP x-business-id header). Falls back to "
                    + "huaweicloud.apm-business-id config when null.", required = false)
            Long businessId,
            @ToolParam(description = "Target alarm record id (from list_apm_alarm_data response). "
                    + "Required and > 0; SDK type is Integer.")
            Integer alarmDataId,
            @ToolParam(description = "Page number, 1-indexed.", required = false)
            Integer page,
            @ToolParam(description = "Page size in [1, 100].", required = false)
            Integer pageSize,
            @ToolParam(description = "Resource region filter (e.g. cn-north-4).", required = false)
            String region) {

        ApmAlarmNotifyRequest req = new ApmAlarmNotifyRequest(
                businessId, alarmDataId, page, pageSize, region);
        try {
            return service.listAlarmNotify(req);
        }
        catch (SmartomException e) {
            LOG.warn("list_alarm_notify failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
