/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * CES 告警动作配置，对应 SDK v2 {@code AlarmHistoryItemV2AlarmActions}。
 *
 * <p>同时用于 {@code alarmActions}（触发时通知）与 {@code okActions}（恢复时通知）。
 *
 * @param type             动作类型字面量，例如 {@code notification} / {@code autoscaling} /
 *                         {@code contact}（已废弃的 {@code groupwatch} / {@code ecsRecovery} 等也可能出现）
 * @param notificationList 通知目标列表（SMN 主题 URN 等）
 * @author h00884391
 * @since 2026-06-10
 */
public record CesAlarmAction(
        String type,
        List<String> notificationList) {
}
