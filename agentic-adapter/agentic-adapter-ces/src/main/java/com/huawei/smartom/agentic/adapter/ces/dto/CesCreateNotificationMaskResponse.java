/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code create_notification_mask} 工具的响应 DTO。
 *
 * @param relationIds        创建成功的关联 ID 列表（可能为空）
 * @param notificationMaskId 创建的屏蔽规则 ID
 * @author h00884391
 * @since 2026-05-28
 */
public record CesCreateNotificationMaskResponse(
        List<String> relationIds,
        String notificationMaskId) {
}
