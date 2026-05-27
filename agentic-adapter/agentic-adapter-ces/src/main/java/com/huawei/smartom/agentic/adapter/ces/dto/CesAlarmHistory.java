/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 单条告警历史摘要。字段名遵循华为云 CES {@code AlarmHistoryInfoResp} 中文档常用的子集，
 * 暴露给 Agent 的最小信息集，避免泄漏 SDK 嵌套结构。
 *
 * @param alarmId          告警规则 ID
 * @param alarmName        告警规则名
 * @param alarmDescription 告警描述，可能为 {@code null}
 * @param alarmLevel       告警级别 [1, 4]
 * @param alarmType        告警类型，可能为 {@code null}
 * @param alarmStatus      告警状态：{@code ok} / {@code alarm} / ...
 * @param namespace        命名空间（从 metric.namespace 提取，可能为 {@code null}）
 * @param metricName       指标名（从 metric.metric_name 提取，可能为 {@code null}）
 * @param triggerTime      触发时间，毫秒时间戳
 * @param updateTime       更新时间，毫秒时间戳
 * @author h00884391
 * @since 2026-05-28
 */
public record CesAlarmHistory(
        String alarmId,
        String alarmName,
        String alarmDescription,
        Integer alarmLevel,
        String alarmType,
        String alarmStatus,
        String namespace,
        String metricName,
        Long triggerTime,
        Long updateTime) {
}
