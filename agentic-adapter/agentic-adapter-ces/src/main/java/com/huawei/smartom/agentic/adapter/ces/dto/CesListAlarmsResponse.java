/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.List;

/**
 * {@code list_alarms} 工具的响应 DTO，包含告警历史列表与总数。
 *
 * @param alarms 告警历史列表，可能为空但不会为 {@code null}
 * @param total  匹配条件的总条数，{@code null} 表示上游未返回
 * @author h00884391
 * @since 2026-05-28
 */
public record CesListAlarmsResponse(
        List<CesAlarmHistory> alarms,
        Integer total) {
}
