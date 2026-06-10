/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * 屏蔽规则响应中的资源分组信息，对应 SDK v2 {@code ResourceCategory}。
 *
 * <p>请求侧的 {@link NotificationMaskResource} 使用具名维度（{@code dimensions: name+value}），
 * 而响应侧的 {@code ResourceCategory} 只携带维度名列表（{@code dimension_names: List<String>}），
 * 因此独立建模，**不**与请求侧 record 复用。
 *
 * @param namespace      CES 命名空间，例如 {@code SYS.ECS}
 * @param dimensionNames 维度名列表（不含取值），例如 {@code ["instance_id"]}
 * @author h00884391
 * @since 2026-06-10
 */
public record CesNotificationMaskResourceCategory(
        String namespace,
        List<String> dimensionNames) {
}
