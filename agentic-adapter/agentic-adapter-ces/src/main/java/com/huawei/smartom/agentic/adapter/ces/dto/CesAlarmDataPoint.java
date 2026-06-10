/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * CES 告警触发时关联的单个数据点，对应 SDK v2 {@code DataPointInfo}。
 *
 * <p>注意 SDK 这里的 {@code time} 字段类型是 {@code String}（ISO 8601 datetime），
 * 与 {@link CesAlarmHistory} 顶层时间字段使用的 {@code OffsetDateTime} 不同——SDK 故意如此，
 * adapter 按 SDK 原样透传。
 *
 * @param time  采样时间（ISO 8601 字符串）
 * @param value 采样值
 * @author h00884391
 * @since 2026-06-10
 */
public record CesAlarmDataPoint(
        String time,
        Double value) {
}
