/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.ces.CesAlarmService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code list_alarms} 能力的 MCP 工具，查询华为云 CES 告警历史。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CesAlarmTool {

    private static final Logger LOG = LoggerFactory.getLogger(CesAlarmTool.class);

    private final CesAlarmService service;

    /**
     * 构造 {@code CesAlarmTool} 实例。
     *
     * @param service CES 告警历史查询服务
     */
    public CesAlarmTool(CesAlarmService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：列出 CES 告警历史，支持按规则、级别、状态、命名空间、时间过滤。
     *
     * @param groupId     资源分组 ID，可选
     * @param alarmId     告警规则 ID，可选
     * @param alarmName   告警规则名，可选
     * @param alarmStatus 告警状态，可选
     * @param alarmLevel  告警级别 1/2/3/4，可选
     * @param namespace   命名空间，可选
     * @param from        起始时间毫秒字符串，可选
     * @param to          截止时间毫秒字符串，可选
     * @param start       分页偏移，可选（默认 0）
     * @param limit       分页大小 [1, 100]，可选（默认 100）
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsResponse}，
     *         失败时返回 {@link ErrorResponse}
     */
    @Tool(
            name = "list_alarms",
            description = """
                    List CES (Cloud Eye Service) alarm history events for Huawei Cloud resources. \
                    Returns rich alarm metadata: rule identity, status (ok / alarm / invalid), \
                    severity (1=critical, 2=major, 3=minor, 4=info), the full trigger condition \
                    (period / filter / comparison / threshold / suppress), all five lifecycle \
                    timestamps (begin / end / first / last / recovery), associated metric \
                    (namespace + dimensions), additional resource info, and alarm/ok action \
                    configurations. Use this to surface recent alarm triggers when correlating \
                    an incident. Filter by alarm rule id/name, status, severity, namespace, or \
                    time range. group_id filtering was a v1-only concept and is no longer \
                    supported; from/to are ISO 8601 datetimes (e.g. 2024-02-11T10:00:00+08:00).""")
    public Object listAlarms(
            @ToolParam(description = "Deprecated. Not supported by v2 API; must be null.",
                    required = false)
            String groupId,
            @ToolParam(description = "Alarm rule id (al-prefixed, length 24).", required = false)
            String alarmId,
            @ToolParam(description = "Alarm rule name, length [1, 128].", required = false)
            String alarmName,
            @ToolParam(description = "Alarm status: ok / alarm / invalid (v2; insufficient_data "
                    + "is no longer supported).", required = false)
            String alarmStatus,
            @ToolParam(description = "Alarm severity: 1=critical, 2=major, 3=minor, 4=info.",
                    required = false)
            Integer alarmLevel,
            @ToolParam(description = "Namespace like SYS.ECS.", required = false)
            String namespace,
            @ToolParam(description = "Start time as ISO 8601 datetime (e.g. "
                    + "2024-02-11T10:00:00+08:00); v2 API replaced v1 millis-string semantics.",
                    required = false)
            String from,
            @ToolParam(description = "End time as ISO 8601 datetime (see 'from').",
                    required = false)
            String to,
            @ToolParam(description = "Page offset (>= 0), default 0.", required = false)
            Integer start,
            @ToolParam(description = "Page size [1, 100], default 100.", required = false)
            Integer limit) {

        CesListAlarmsRequest req = new CesListAlarmsRequest(
                groupId, alarmId, alarmName, alarmStatus, alarmLevel,
                namespace, from, to, start, limit);
        try {
            return service.listAlarms(req);
        }
        catch (SmartomException e) {
            LOG.warn("list_alarms failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
