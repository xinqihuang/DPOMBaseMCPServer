/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskDimension;
import com.huawei.smartom.agentic.monitoring.ces.CesNotificationMaskService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code list_notification_masks} 能力的 MCP 工具：分页查询告警屏蔽规则。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CesListNotificationMasksTool implements McpTool {

    private final CesNotificationMaskService service;

    /**
     * 构造 {@code CesListNotificationMasksTool} 实例。
     *
     * @param service CES 告警屏蔽规则服务
     */
    public CesListNotificationMasksTool(CesNotificationMaskService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按筛选条件分页查询告警屏蔽规则。
     *
     * @param offset        分页偏移
     * @param limit         分页大小
     * @param sortKey       排序键
     * @param sortDir       排序顺序
     * @param relationType  关联类型，可选
     * @param relationIds   关联编号列表，可选
     * @param metricName    指标名，可选
     * @param resourceLevel 资源粒度，可选
     * @param maskId        屏蔽规则 ID，可选
     * @param maskName      屏蔽规则名，可选
     * @param maskStatus    屏蔽状态，可选
     * @param resourceId    资源 ID，可选
     * @param namespace     命名空间，可选
     * @param dimensions    资源维度列表，可选
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "list_notification_masks",
            description = """
                    Query CES (Cloud Eye Service) alarm notification mask rules with paging and \
                    optional filters. Use this to find existing mask ids before calling \
                    delete_notification_masks, or to audit currently active shields. Filter by \
                    relation_type / mask_name / mask_status (MASK_EFFECTIVE — active now, or \
                    MASK_INEFFECTIVE — created but outside its time window), namespace, \
                    resource_id, or specific dimensions. Returns mask metadata (id, name, type, \
                    time window, etc.) plus a total 'count'.""")
    public Object listNotificationMasks(
            @ToolParam(description = "Page offset [0, 10000], default 0.", required = false)
            Integer offset,
            @ToolParam(description = "Page size [1, 100], default 100.", required = false)
            Integer limit,
            @ToolParam(description = "Sort key: create_time / update_time.", required = false)
            String sortKey,
            @ToolParam(description = "Sort direction: ASC / DESC.", required = false)
            String sortDir,
            @ToolParam(description = "Relation type filter: ALARM_RULE / RESOURCE / "
                    + "RESOURCE_POLICY_NOTIFICATION / RESOURCE_POLICY_ALARM / DEFAULT.",
                    required = false)
            String relationType,
            @ToolParam(description = "Relation ids (alarm rule / policy ids).", required = false)
            List<String> relationIds,
            @ToolParam(description = "Metric name filter.", required = false)
            String metricName,
            @ToolParam(description = "Resource granularity: 'dimension' or 'product'.",
                    required = false)
            String resourceLevel,
            @ToolParam(description = "Specific notification_mask_id.", required = false)
            String maskId,
            @ToolParam(description = "Mask rule name.", required = false)
            String maskName,
            @ToolParam(description = "Mask status: MASK_EFFECTIVE / MASK_INEFFECTIVE.",
                    required = false)
            String maskStatus,
            @ToolParam(description = "Resource dimension value (e.g. an instance_id).",
                    required = false)
            String resourceId,
            @ToolParam(description = "Namespace, e.g. SYS.ECS.", required = false)
            String namespace,
            @ToolParam(description = "Resource dimensions list [{name, value}].",
                    required = false)
            List<NotificationMaskDimension> dimensions) {

        CesListNotificationMasksRequest req = new CesListNotificationMasksRequest(
                offset, limit, sortKey, sortDir, relationType, relationIds, metricName,
                resourceLevel, maskId, maskName, maskStatus, resourceId, namespace, dimensions);
        return ToolCallSupport.execute("list_notification_masks", () -> service.listNotificationMasks(req));
    }
}
