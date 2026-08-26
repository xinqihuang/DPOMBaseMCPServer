/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * APM 整条告警规则状态更新请求。
 *
 * @param alarmRuleId 告警规则 ID，不是告警事件 ID、实例 ID 或模板 ID
 * @param enable      {@code true} 启用整条规则，{@code false} 关闭整条规则
 * @author h00884391
 * @since 2026-08-26
 */
public record ApmAlarmRuleStatusRequest(Long alarmRuleId, Boolean enable) {
}
