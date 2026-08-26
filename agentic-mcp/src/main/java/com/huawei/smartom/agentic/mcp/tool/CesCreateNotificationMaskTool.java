/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskProductMetric;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskResource;
import com.huawei.smartom.agentic.monitoring.ces.CesNotificationMaskService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code create_notification_mask} 能力的 MCP 工具：创建告警通知屏蔽规则。
 *
 * <p>典型用例：变更窗口前临时屏蔽相关告警，避免变更引起的误告警。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
@Profile("action-enabled & !production")
@ConditionalOnProperty(prefix = "dpom.mcp", name = "write-tools-enabled", havingValue = "true")
public class CesCreateNotificationMaskTool implements McpTool {

    private final CesNotificationMaskService service;

    /**
     * 构造 {@code CesCreateNotificationMaskTool} 实例。
     *
     * @param service CES 告警屏蔽规则服务
     */
    public CesCreateNotificationMaskTool(CesNotificationMaskService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：创建一条告警通知屏蔽规则。
     *
     * @param maskName          屏蔽规则名称
     * @param relationType      关联类型
     * @param relationIds       关联编号列表（告警规则 / 告警策略 ID），可选
     * @param resources         关联资源列表，可选
     * @param metricNames       关联指标名列表，可选
     * @param productMetrics    按云产品屏蔽时的指标信息列表，可选
     * @param resourceLevel     资源粒度，可选
     * @param productName       云产品名，可选
     * @param maskType          屏蔽类型
     * @param startDate         起始日期 {@code yyyy-MM-dd}，可选
     * @param startTime         起始时间 {@code HH:mm:ss}，可选
     * @param endDate           截止日期 {@code yyyy-MM-dd}，可选
     * @param endTime           截止时间 {@code HH:mm:ss}，可选
     * @param effectiveTimezone 时区字符串，可选
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "create_notification_mask",
            description = """
                    Create a CES (Cloud Eye Service) alarm notification mask rule. Use this to \
                    SHIELD alarm notifications during planned changes / maintenance windows so \
                    they don't fire false-positive notifications to on-call. Choose 'mask_type' \
                    = START_END_TIME (one-off window), FOREVER_TIME (until deleted), or \
                    CYCLE_TIME (recurring). Choose 'relation_type' = ALARM_RULE (mask specific \
                    alarm rules — supply 'relation_ids'), RESOURCE (mask specific resources — \
                    supply 'resources'), or RESOURCE_POLICY_NOTIFICATION / \
                    RESOURCE_POLICY_ALARM / EVENT.SYS. After the maintenance window, call \
                    delete_notification_masks to remove the shield.""")
    public Object createNotificationMask(
            @ToolParam(description = "Mask rule name; [letters/digits/CJK/-/_], length [1, 64].")
            String maskName,
            @ToolParam(description = "Relation type: ALARM_RULE / RESOURCE / "
                    + "RESOURCE_POLICY_NOTIFICATION / RESOURCE_POLICY_ALARM / EVENT.SYS.")
            String relationType,
            @ToolParam(description = "Alarm rule ids (ALARM_RULE) or policy ids "
                    + "(RESOURCE_POLICY_*).", required = false)
            List<String> relationIds,
            @ToolParam(description = "Resources to shield (each {namespace, dimensions}).",
                    required = false)
            List<NotificationMaskResource> resources,
            @ToolParam(description = "Metric names (optional for RESOURCE; if absent, all "
                    + "metrics on the resource are shielded).", required = false)
            List<String> metricNames,
            @ToolParam(description = "Product-level metric filters {dimension_name, "
                    + "metric_name}.", required = false)
            List<NotificationMaskProductMetric> productMetrics,
            @ToolParam(description = "Resource granularity: 'dimension' or 'product'.",
                    required = false)
            String resourceLevel,
            @ToolParam(description = "Product name when resource_level=product.",
                    required = false)
            String productName,
            @ToolParam(description = "Mask type: START_END_TIME / FOREVER_TIME / CYCLE_TIME.")
            String maskType,
            @ToolParam(description = "Start date yyyy-MM-dd (required for time-bounded "
                    + "mask_type).", required = false)
            String startDate,
            @ToolParam(description = "Start time HH:mm:ss (required for time-bounded "
                    + "mask_type).", required = false)
            String startTime,
            @ToolParam(description = "End date yyyy-MM-dd (required for time-bounded "
                    + "mask_type).", required = false)
            String endDate,
            @ToolParam(description = "End time HH:mm:ss (required for time-bounded mask_type).",
                    required = false)
            String endTime,
            @ToolParam(description = "Effective timezone, e.g. 'GMT+08:00'.", required = false)
            String effectiveTimezone) {

        CesCreateNotificationMaskRequest req = new CesCreateNotificationMaskRequest(
                maskName, relationType, relationIds, resources, metricNames, productMetrics,
                resourceLevel, productName, maskType,
                startDate, startTime, endDate, endTime, effectiveTimezone);
        return ToolCallSupport.execute("create_notification_mask", () -> service.createNotificationMask(req));
    }
}
