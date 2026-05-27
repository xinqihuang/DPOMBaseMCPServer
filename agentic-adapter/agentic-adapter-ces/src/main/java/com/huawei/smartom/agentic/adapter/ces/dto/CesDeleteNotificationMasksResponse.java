/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code delete_notification_masks} 工具的响应 DTO。
 *
 * @param notificationMaskIds 删除成功的屏蔽规则 ID 列表（可能为空但不会为 {@code null}）
 * @author h00884391
 * @since 2026-05-28
 */
public record CesDeleteNotificationMasksResponse(List<String> notificationMaskIds) {
}
