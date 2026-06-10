/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 趋势图单个数据点，对应 SDK v1 {@code FrontPoint} 的无损投影
 * （T19 §4.1 准则，2 字段全保留）。
 *
 * <p>{@code value} 在 SDK 端为 {@link Object}（折线 y 值可能是数字、字符串等），
 * DTO 保留 {@link Object} 类型以避免类型假设导致的信息丢失；下游 Jackson 会按
 * 运行时实际类型序列化（{@code Long} / {@code Double} / {@code String} 等）。
 *
 * @param time  时间戳（毫秒）
 * @param value 点的值，运行时类型由上游决定（不假设为 Number）
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmTrendPoint(
        Long time,
        Object value) {
}
