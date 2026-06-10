/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code list_apm_alarm_data} 工具的响应 DTO，对应 SDK {@code ListAlarmDataResponse} 的无损投影。
 *
 * @param alarms     告警记录列表，可能为空但不会为 {@code null}
 * @param totalCount 满足过滤条件的总条数（来自上游 {@code total_count}），可能为 {@code null}
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmAlarmDataResponse(
        List<ApmAlarm> alarms,
        Integer totalCount) {
}
