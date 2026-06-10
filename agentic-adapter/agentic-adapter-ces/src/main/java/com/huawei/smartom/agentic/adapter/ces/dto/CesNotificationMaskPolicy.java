/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 屏蔽规则关联的告警策略快照，对应 SDK v2 {@code PoliciesInListResp}。
 *
 * <p>{@code period} 与 {@code suppressDuration} 在 SDK 中是 Integer-backed 枚举
 * （{@code Period} / {@code SuppressDuration}），DTO 暴露为 {@code Integer}。
 *
 * @param alarmPolicyId      告警策略 ID
 * @param metricName         指标名
 * @param extraInfo          额外指标元信息
 * @param period             指标聚合周期（秒）
 * @param filter             聚合方式
 * @param comparisonOperator 比较运算符
 * @param value              告警阈值（单级阈值场景）
 * @param hierarchicalValue  分级告警阈值（多级阈值场景）
 * @param unit               阈值单位
 * @param count              连续触发次数
 * @param type               策略类型字面量
 * @param suppressDuration   告警抑制周期（秒）
 * @param alarmLevel         告警级别 1/2/3/4
 * @param selectedUnit       展示单位
 * @author h00884391
 * @since 2026-06-10
 */
public record CesNotificationMaskPolicy(
        String alarmPolicyId,
        String metricName,
        CesNotificationMaskMetricExtraInfo extraInfo,
        Integer period,
        String filter,
        String comparisonOperator,
        Double value,
        CesNotificationMaskHierarchicalValue hierarchicalValue,
        String unit,
        Integer count,
        String type,
        Integer suppressDuration,
        Integer alarmLevel,
        String selectedUnit) {
}
