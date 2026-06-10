/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code list_alarm_notify} 工具的请求 DTO，对应华为云 APM
 * {@code POST /v1/apm2/openapi/alarm/data/get-alarm-notify-list}。
 *
 * <p>SDK 类型注意：{@code alarmDataId} 在上游 SDK 是 {@code Integer}，而
 * {@link ApmAlarm#id()}（来自 {@code list_apm_alarm_data} 响应）是 {@code Long}。
 * Agent 把 {@code Long} 传过来时若超过 {@code Integer.MAX_VALUE} 会被 SDK 截断 / 拒绝；
 * 本工具不修正这个上游设计上的不一致——按 T19 §4.1 准则 DTO 类型直接贴齐 SDK。
 *
 * @param businessId    APM 业务 id（HTTP {@code x-business-id} 头），可选；为 null 时回落到
 *                      {@code huaweicloud.apm-business-id} 配置项默认值
 * @param alarmDataId   告警记录 id，来自 {@link ApmAlarm#id()}；必填且 > 0
 * @param page          页码 1 起始；可选
 * @param pageSize      单页大小 [1, 100]；可选
 * @param region        资源 region 过滤，可选
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmAlarmNotifyRequest(
        Long businessId,
        Integer alarmDataId,
        Integer page,
        Integer pageSize,
        String region) {
}
