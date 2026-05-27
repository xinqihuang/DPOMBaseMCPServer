/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code create_notification_mask} 工具的请求 DTO，对应华为云 CES
 * {@code BatchUpdateNotificationMasks} 接口入参。
 *
 * <p>字段语义参见
 * <a href="https://support.huaweicloud.com/intl/zh-cn/api-ces/BatchUpdateNotificationMasks.html">华为云文档</a>。
 *
 * @param maskName          屏蔽规则名称，长度 [1, 64]，仅字母/数字/汉字/-/_
 * @param relationType      关联类型：{@code ALARM_RULE} / {@code RESOURCE} /
 *                          {@code RESOURCE_POLICY_NOTIFICATION} /
 *                          {@code RESOURCE_POLICY_ALARM} / {@code EVENT.SYS}
 * @param relationIds       关联编号列表（告警规则 ID 或告警策略 ID）
 * @param resources         关联资源列表（按维度屏蔽时使用）
 * @param metricNames       指标名列表（{@code RESOURCE} 关联时可选）
 * @param productMetrics    按云产品屏蔽时的指标信息列表
 * @param resourceLevel     资源粒度：{@code dimension} / {@code product}
 * @param productName       云产品名（{@code resourceLevel=product} 时使用）
 * @param maskType          屏蔽类型：{@code START_END_TIME} / {@code FOREVER_TIME} /
 *                          {@code CYCLE_TIME}
 * @param startDate         起始日期，格式 {@code yyyy-MM-dd}
 * @param startTime         起始时间，格式 {@code HH:mm:ss}
 * @param endDate           截止日期，格式 {@code yyyy-MM-dd}
 * @param endTime           截止时间，格式 {@code HH:mm:ss}
 * @param effectiveTimezone 时区，例如 {@code GMT+08:00}，长度 [1, 16]
 * @author h00884391
 * @since 2026-05-28
 */
public record CesCreateNotificationMaskRequest(
        String maskName,
        String relationType,
        List<String> relationIds,
        List<NotificationMaskResource> resources,
        List<String> metricNames,
        List<NotificationMaskProductMetric> productMetrics,
        String resourceLevel,
        String productName,
        String maskType,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        String effectiveTimezone) {
}
