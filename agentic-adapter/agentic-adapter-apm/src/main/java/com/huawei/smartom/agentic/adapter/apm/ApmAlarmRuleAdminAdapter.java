/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusResponse;

/**
 * 华为云 APM 告警规则管理适配器。
 *
 * <p>该接口更新的是整条告警规则，可能同时影响多个服务与实例；规则 ID 不得替换为告警事件 ID、
 * 实例 ID 或模板 ID。实现不自动重试写请求，调用方必须基于目标状态自行决定是否安全重试。
 *
 * <p>该接口仅供服务内部显式调用，不会自动注册为 MCP 工具。
 *
 * @author h00884391
 * @since 2026-08-26
 */
public interface ApmAlarmRuleAdminAdapter {

    /**
     * 启用或关闭指定 ID 对应的整条 APM 告警规则。
     *
     * @param request 规则 ID 与目标启用状态
     * @return 已确认的状态更新结果
     */
    ApmAlarmRuleStatusResponse updateAlarmRuleStatus(ApmAlarmRuleStatusRequest request);
}
