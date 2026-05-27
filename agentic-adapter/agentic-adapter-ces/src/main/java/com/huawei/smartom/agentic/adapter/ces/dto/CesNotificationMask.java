/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * 单条屏蔽规则摘要。字段子集对齐华为云 CES {@code ListNotificationMaskRespNotificationMasks}。
 *
 * @param notificationMaskId 屏蔽规则 ID
 * @param maskName           屏蔽规则名称
 * @param relationType       关联类型
 * @param relationId         关联编号（告警规则 / 告警策略 ID）
 * @param resourceLevel      资源粒度：{@code dimension} / {@code product}
 * @param productName        云产品名（{@code resourceLevel=product} 时使用）
 * @param maskStatus         屏蔽状态：{@code MASK_EFFECTIVE} / {@code MASK_INEFFECTIVE}
 * @param maskType           屏蔽类型：{@code START_END_TIME} / {@code FOREVER_TIME} /
 *                           {@code CYCLE_TIME}
 * @param metricNames        关联指标名列表（可能为 {@code null}）
 * @param productMetrics     按云产品屏蔽时的指标信息列表（可能为 {@code null}）
 * @param startDate          起始日期，{@code yyyy-MM-dd}
 * @param startTime          起始时间，{@code HH:mm:ss}
 * @param endDate            截止日期，{@code yyyy-MM-dd}
 * @param endTime            截止时间，{@code HH:mm:ss}
 * @param effectiveTimezone  时区
 * @author h00884391
 * @since 2026-05-28
 */
public record CesNotificationMask(
        String notificationMaskId,
        String maskName,
        String relationType,
        String relationId,
        String resourceLevel,
        String productName,
        String maskStatus,
        String maskType,
        List<String> metricNames,
        List<NotificationMaskProductMetric> productMetrics,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        String effectiveTimezone) {
}
