/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code list_notification_masks} 工具的响应 DTO。
 *
 * @param notificationMasks 屏蔽规则列表（可能为空但不会为 {@code null}）
 * @param count             总条数（来自上游），可能为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record CesListNotificationMasksResponse(
        List<CesNotificationMask> notificationMasks,
        Integer count) {
}
