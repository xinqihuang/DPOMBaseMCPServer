/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 单条 CES 告警历史记录，对应 SDK v2 {@code AlarmHistoryItemV2} 的无损投影
 * （PR T19 PR-1 切到 v2 SDK，详见 {@code docs/tasks/T19-lossless-dto-remediation.md}）。
 *
 * <p>前 10 个字段（{@code alarmId} ... {@code updateTime}）沿用 v1 时代的命名以保持
 * Agent 向后兼容；其余 13 个字段是 v2 引入的新信号（{@code recordId} / 五个时间戳 /
 * {@code metric} / {@code condition} / {@code additionalInfo} / 动作配置 / 数据点）。
 *
 * <p>本期 SDK 钉 3.1.177，**{@code mask_status} 字段不存在**——3.1.194+ 才新增；待整体
 * SDK 升级后再补字段。
 *
 * @param alarmId            告警规则 ID（v2 {@code alarm_id}）
 * @param alarmName          告警规则名（v2 {@code name}）
 * @param alarmDescription   告警描述。v2 接口不提供该字段，恒为 {@code null}，保留以兼容历史调用方
 * @param alarmLevel         告警级别 1/2/3/4（v2 {@code level}，SDK 是 Integer-backed 枚举，取 {@code getValue()}）
 * @param alarmType          告警规则类型字面量（v2 {@code type}，{@code .getValue()}）
 * @param alarmStatus        告警状态字面量（v2 {@code status}，{@code .getValue()}），
 *                           v2 取值 {@code ok/alarm/invalid/ok_manual}
 * @param namespace          指标命名空间（从 {@code metric.namespace} 派生）
 * @param metricName         指标 id（从 {@code metric.metric_name} 派生）
 * @param triggerTime        触发时间毫秒（从 v2 {@code first_alarm_time} 派生），可能为 {@code null}
 * @param updateTime         最近一次告警时间毫秒（从 v2 {@code last_alarm_time} 派生），可能为 {@code null}
 * @param recordId           告警记录 ID（v2 新增，{@code record_id}）
 * @param actionEnabled      是否启用告警动作（v2 新增）
 * @param beginTime          告警开始时间（v2 {@code begin_time}）
 * @param endTime            告警结束时间（v2 {@code end_time}）
 * @param firstAlarmTime     首次告警时间（v2 {@code first_alarm_time}）
 * @param lastAlarmTime      最近告警时间（v2 {@code last_alarm_time}）
 * @param alarmRecoveryTime  告警恢复时间（v2 {@code alarm_recovery_time}）
 * @param metric             指标信息（{@code namespace}/{@code metricName} 的来源对象）
 * @param condition          告警触发条件（含 period/filter/comparisonOperator/value/...）
 * @param additionalInfo     附加资源信息
 * @param alarmActions       告警触发时的动作列表
 * @param okActions          告警恢复时的动作列表
 * @param dataPoints         告警关联的指标数据点
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
        Long updateTime,
        String recordId,
        Boolean actionEnabled,
        OffsetDateTime beginTime,
        OffsetDateTime endTime,
        OffsetDateTime firstAlarmTime,
        OffsetDateTime lastAlarmTime,
        OffsetDateTime alarmRecoveryTime,
        CesAlarmMetric metric,
        CesAlarmCondition condition,
        CesAlarmAdditionalInfo additionalInfo,
        List<CesAlarmAction> alarmActions,
        List<CesAlarmAction> okActions,
        List<CesAlarmDataPoint> dataPoints) {
}
