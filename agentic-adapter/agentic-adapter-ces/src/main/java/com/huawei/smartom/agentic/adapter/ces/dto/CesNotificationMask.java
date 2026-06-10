/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * 单条屏蔽规则记录，对应 SDK v2 {@code ListNotificationMaskRespNotificationMasks} 的无损投影
 * （T19 PR-2 补齐之前丢失的 5 个字段：{@code resourceType} / {@code resources} /
 * {@code createTime} / {@code updateTime} / {@code policies}）。
 *
 * <p>SDK 中的 String-backed 枚举（{@code relationType} / {@code resourceType} /
 * {@code resourceLevel} / {@code maskStatus} / {@code maskType}）按 {@code .getValue()} 取
 * 字面量；日期字段（{@code startDate} / {@code endDate}）从 SDK 的 {@code LocalDate} 序列化
 * 为 {@code yyyy-MM-dd} 字符串，便于 JSON 透传。
 *
 * @param notificationMaskId 屏蔽规则 ID
 * @param maskName           屏蔽规则名称
 * @param relationType       关联类型字面量
 * @param relationId         关联编号（告警规则 / 告警策略 ID）
 * @param resourceType       资源类型字面量（v2 新增覆盖）
 * @param metricNames        关联指标名列表，可能为 {@code null}
 * @param productMetrics     按云产品屏蔽时的指标信息列表，可能为 {@code null}
 * @param resourceLevel      资源粒度字面量：{@code dimension} / {@code product}
 * @param productName        云产品名（{@code resourceLevel=product} 时使用）
 * @param resources          按资源分组屏蔽时的目标资源列表（v2 新增覆盖）
 * @param maskStatus         屏蔽状态字面量：{@code MASK_EFFECTIVE} / {@code MASK_INEFFECTIVE}
 * @param maskType           屏蔽类型字面量：{@code START_END_TIME} / {@code FOREVER_TIME} /
 *                           {@code CYCLE_TIME}
 * @param createTime         创建时间，毫秒时间戳（v2 新增覆盖）
 * @param updateTime         更新时间，毫秒时间戳（v2 新增覆盖）
 * @param startDate          起始日期，{@code yyyy-MM-dd}
 * @param startTime          起始时间，{@code HH:mm:ss}
 * @param endDate            截止日期，{@code yyyy-MM-dd}
 * @param endTime            截止时间，{@code HH:mm:ss}
 * @param effectiveTimezone  时区
 * @param policies           关联的告警策略快照列表（v2 新增覆盖）
 * @author h00884391
 * @since 2026-05-28
 */
public record CesNotificationMask(
        String notificationMaskId,
        String maskName,
        String relationType,
        String relationId,
        String resourceType,
        List<String> metricNames,
        List<NotificationMaskProductMetric> productMetrics,
        String resourceLevel,
        String productName,
        List<CesNotificationMaskResourceCategory> resources,
        String maskStatus,
        String maskType,
        Long createTime,
        Long updateTime,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        String effectiveTimezone,
        List<CesNotificationMaskPolicy> policies) {
}
