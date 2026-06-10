/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 单条 APM 告警通知投递记录，对应 SDK v1 {@code FrontAlarmNotifyResult} 的无损投影
 * （T19 §4.1 准则，8 字段全保留）。
 *
 * <p>{@code notifyStatus} 为 {@code true} 表示通知送达成功，{@code false} 表示失败。
 * {@code notifyType} 为通道字面量（如 {@code SMS} / {@code EMAIL} / {@code WEBHOOK}，
 * 具体取值由上游决定，DTO 不做集合校验）。
 *
 * @param id                 通知记录 id
 * @param gmtCreate          创建时间字符串
 * @param notifyType         通知类型字面量
 * @param alarmRuleId        告警规则 id
 * @param templateId         通知模板 id
 * @param alarmDataEventId   关联告警事件 id（对应 {@link ApmAlarm#regionAlarmEventId()}）
 * @param notifyStatus       通知是否送达成功
 * @param alarmContent       告警内容快照
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmAlarmNotification(
        Long id,
        String gmtCreate,
        String notifyType,
        Long alarmRuleId,
        Long templateId,
        Long alarmDataEventId,
        Boolean notifyStatus,
        String alarmContent) {
}
