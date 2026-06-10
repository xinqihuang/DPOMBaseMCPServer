/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code list_apm_alarm_data} 工具的请求 DTO，对应华为云 APM
 * {@code POST /v1/apm2/openapi/alarm/data/get-alarm-data-list}。
 *
 * <p>注意区分两个 {@code business_id}：
 * <ul>
 *   <li>{@link #businessId} —— HTTP header {@code x-business-id}，APM 业务（应用）id，
 *       用作 SDK 鉴权上下文；为 {@code null} 时回落到 {@code huaweicloud.apm-business-id}
 *       配置项默认值</li>
 *   <li>{@link #businessIdFilter} —— body 中的 {@code business_id} 字段，仅作为过滤条件，
 *       可选；两者完全独立</li>
 * </ul>
 *
 * @param businessId        APM 业务 id（header），可选
 * @param page              页码，1 起始；可选，service 校验 ≥ 1
 * @param pageSize          单页大小，[1, 100]；可选
 * @param region            资源 region 过滤，可选
 * @param appName           应用名过滤，可选
 * @param businessIdFilter  业务 id 过滤（body 中的 business_id），可选
 * @param monitorItemId     监控项 id 过滤，可选
 * @param status            告警状态过滤，可选
 * @param alarmLevel        告警级别字符串过滤，可选
 * @param keyword           关键字模糊匹配，可选
 * @param alarmStartTime    告警起始时间字符串，可选（SDK 为 String，按上游透传）
 * @param alarmEndTime      告警截止时间字符串，可选
 * @param collectorId       采集器 id 过滤，可选
 * @param ipAddress         实例 IP 过滤，可选
 * @param envList           环境 id 列表过滤，可选
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmAlarmDataRequest(
        Long businessId,
        Integer page,
        Integer pageSize,
        String region,
        String appName,
        Long businessIdFilter,
        Long monitorItemId,
        String status,
        String alarmLevel,
        String keyword,
        String alarmStartTime,
        String alarmEndTime,
        Integer collectorId,
        String ipAddress,
        List<Long> envList) {
}
