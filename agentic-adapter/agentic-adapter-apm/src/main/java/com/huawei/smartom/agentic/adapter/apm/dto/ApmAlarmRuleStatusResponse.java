/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * APM 整条告警规则状态更新结果。
 *
 * @param alarmRuleId    已操作的告警规则 ID
 * @param enabled        操作后的目标启用状态
 * @param success        上游是否返回成功契约
 * @param upstreamMarker 上游成功标记，当前文档值为 {@code ok}
 * @author h00884391
 * @since 2026-08-26
 */
public record ApmAlarmRuleStatusResponse(
        Long alarmRuleId,
        boolean enabled,
        boolean success,
        String upstreamMarker) {
}
