/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code list_notification_masks} 工具的请求 DTO，对应华为云 CES
 * {@code ListNotificationMasks} 接口入参（分页查询 + body 过滤）。
 *
 * @param offset        分页偏移量，{@code null} 时使用 0；范围 [0, 10000]
 * @param limit         分页大小，{@code null} 时使用 100；范围 [1, 100]
 * @param sortKey       排序键：{@code create_time} / {@code update_time}，可选
 * @param sortDir       排序顺序：{@code ASC} / {@code DESC}，可选
 * @param relationType  关联类型（查询版本）：{@code ALARM_RULE} / {@code RESOURCE} /
 *                      {@code RESOURCE_POLICY_NOTIFICATION} /
 *                      {@code RESOURCE_POLICY_ALARM} / {@code DEFAULT}，可选
 * @param relationIds   关联编号列表，可选
 * @param metricName    指标名，可选
 * @param resourceLevel 资源粒度：{@code dimension} / {@code product}，可选
 * @param maskId        屏蔽规则 ID，可选
 * @param maskName      屏蔽规则名，可选
 * @param maskStatus    屏蔽状态：{@code MASK_EFFECTIVE} / {@code MASK_INEFFECTIVE}，可选
 * @param resourceId    资源维度值，可选
 * @param namespace     命名空间（如 {@code SYS.ECS}），可选
 * @param dimensions    资源维度列表，可选
 * @author h00884391
 * @since 2026-05-28
 */
public record CesListNotificationMasksRequest(
        Integer offset,
        Integer limit,
        String sortKey,
        String sortDir,
        String relationType,
        List<String> relationIds,
        String metricName,
        String resourceLevel,
        String maskId,
        String maskName,
        String maskStatus,
        String resourceId,
        String namespace,
        List<NotificationMaskDimension> dimensions) {

    /**
     * 紧凑构造函数：仅设置默认 offset / limit，不做业务校验。
     */
    public CesListNotificationMasksRequest {
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 100;
        }
    }
}
