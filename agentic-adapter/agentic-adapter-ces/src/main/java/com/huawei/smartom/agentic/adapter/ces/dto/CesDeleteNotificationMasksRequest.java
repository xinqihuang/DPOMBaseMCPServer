/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code delete_notification_masks} 工具的请求 DTO，对应华为云 CES
 * {@code BatchDeleteNotificationMasks} 接口入参。
 *
 * @param notificationMaskIds 屏蔽规则 ID 列表，长度 [1, 100]
 * @author h00884391
 * @since 2026-05-28
 */
public record CesDeleteNotificationMasksRequest(List<String> notificationMaskIds) {
}
