/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksRequest;
import com.huawei.smartom.agentic.monitoring.ces.CesNotificationMaskService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code delete_notification_masks} 能力的 MCP 工具：批量删除告警屏蔽规则。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CesDeleteNotificationMasksTool implements McpTool {

    private final CesNotificationMaskService service;

    /**
     * 构造 {@code CesDeleteNotificationMasksTool} 实例。
     *
     * @param service CES 告警屏蔽规则服务
     */
    public CesDeleteNotificationMasksTool(CesNotificationMaskService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：批量删除告警通知屏蔽规则。
     *
     * @param notificationMaskIds 屏蔽规则 ID 列表，长度 [1, 100]
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "delete_notification_masks",
            description = """
                    Batch-delete CES alarm notification mask rules by id. Use this to LIFT \
                    alarm shielding after a maintenance window completes, restoring normal \
                    notification flow. Pass up to 100 mask ids at a time. Returns the ids that \
                    were actually deleted (mismatches with the input typically mean the id no \
                    longer exists).""")
    public Object deleteNotificationMasks(
            @ToolParam(description = "Notification mask ids to delete; size [1, 100].")
            List<String> notificationMaskIds) {

        CesDeleteNotificationMasksRequest req =
                new CesDeleteNotificationMasksRequest(notificationMaskIds);
        return ToolCallSupport.execute("delete_notification_masks", () -> service.deleteNotificationMasks(req));
    }
}
