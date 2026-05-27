/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * 屏蔽规则中的资源信息。
 *
 * @param namespace  命名空间（如 {@code SYS.ECS}）
 * @param dimensions 资源维度列表，最多 4 个
 * @author h00884391
 * @since 2026-05-28
 */
public record NotificationMaskResource(
        String namespace,
        List<NotificationMaskDimension> dimensions) {
}
