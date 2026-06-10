/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 分级告警阈值，对应 SDK v2 {@code HierarchicalValue}。
 *
 * <p>每个字段代表不同严重级别下的告警阈值；为 {@code null} 表示该级别未配置。
 *
 * @param critical 紧急级别阈值（level=1）
 * @param major    重要级别阈值（level=2）
 * @param minor    次要级别阈值（level=3）
 * @param info     提示级别阈值（level=4）
 * @author h00884391
 * @since 2026-06-10
 */
public record CesNotificationMaskHierarchicalValue(
        Double critical,
        Double major,
        Double minor,
        Double info) {
}
