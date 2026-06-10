/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * CES 告警关联的附加资源信息，对应 SDK v2 {@code AdditionalInfo}。
 *
 * @param resourceId   资源 ID，可能为空串
 * @param resourceName 资源名称，可能为空串
 * @param eventId      事件 ID，仅事件类告警有值
 * @author h00884391
 * @since 2026-06-10
 */
public record CesAlarmAdditionalInfo(
        String resourceId,
        String resourceName,
        String eventId) {
}
