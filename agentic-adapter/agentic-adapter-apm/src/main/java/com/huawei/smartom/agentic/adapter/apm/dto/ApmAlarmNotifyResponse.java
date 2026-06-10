/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code list_alarm_notify} 工具的响应 DTO，对应 SDK {@code ListAlarmNotifyResponse} 的无损投影。
 *
 * @param notifications  通知投递记录列表，可能为空但不会为 {@code null}
 * @param totalCount     满足条件的总条数，可能为 {@code null}
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmAlarmNotifyResponse(
        List<ApmAlarmNotification> notifications,
        Integer totalCount) {
}
