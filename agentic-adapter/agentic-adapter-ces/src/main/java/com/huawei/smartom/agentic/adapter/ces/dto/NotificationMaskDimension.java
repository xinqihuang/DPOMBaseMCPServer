/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 屏蔽规则中的资源维度，例如 {@code (name=instance_id, value=...)}。
 *
 * @param name  维度名，字母开头，长度 [1, 32]
 * @param value 维度值，长度 [1, 256]
 * @author h00884391
 * @since 2026-05-28
 */
public record NotificationMaskDimension(String name, String value) {
}
