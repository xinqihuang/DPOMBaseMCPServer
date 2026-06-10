/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * CES 告警触发条件，对应 SDK v2 {@code AlarmHistoryItemV2Condition}。
 *
 * <p>无损映射：SDK 的 {@code period} 与 {@code suppressDuration} 在 SDK 中是 Integer-backed 枚举
 * （PeriodEnum / SuppressDurationEnum），DTO 暴露为 {@code Integer} 取其 {@code .getValue()}，
 * 保持枚举可扩展且对 Agent 友好。
 *
 * @param period             指标聚合周期（秒）：0/1/300/1200/3600/14400/86400
 * @param filter             聚合方式，如 {@code average}
 * @param comparisonOperator 比较运算符，如 {@code >=}
 * @param value              告警阈值
 * @param unit               阈值单位
 * @param count              连续触发次数
 * @param suppressDuration   告警抑制周期（秒）：0/300/600/900/1800/3600/10800/21600/43200/86400
 * @author h00884391
 * @since 2026-06-10
 */
public record CesAlarmCondition(
        Integer period,
        String filter,
        String comparisonOperator,
        Double value,
        String unit,
        Integer count,
        Integer suppressDuration) {
}
