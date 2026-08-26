/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;

import java.time.Instant;

/**
 * Investigation 与预算连接查询的类型化行。
 *
 * @param investigationId 调查身份
 * @param incidentId 事件身份
 * @param status 状态
 * @param aggregateVersion 聚合版本
 * @param authorityService 权威服务
 * @param authorityEpoch 权威 epoch
 * @param authorityActiveFrom 权威启用时间
 * @param activeRunId 当前运行身份
 * @param updatedAt 更新时间
 * @param maxSteps 最大步骤数
 * @param maxToolCalls 最大工具调用数
 * @param maxTokens 最大 token 数
 * @param maxDurationSeconds 最大持续秒数
 * @param usedSteps 已用步骤数
 * @param usedToolCalls 已用工具调用数
 * @param usedTokens 已用 token 数
 * @param usedDurationSeconds 已用持续秒数
 * @author Codex
 * @since 2026-08-25
 */
public record InvestigationRow(String investigationId, String incidentId, String status,
                               long aggregateVersion, String authorityService, String authorityEpoch,
                               Instant authorityActiveFrom, String activeRunId, Instant updatedAt,
                               int maxSteps, int maxToolCalls, long maxTokens, long maxDurationSeconds,
                               int usedSteps, int usedToolCalls, long usedTokens, long usedDurationSeconds) {

    /**
     * 转换为框架无关领域聚合。
     *
     * @return Investigation 聚合
     */
    public Investigation toDomain() {
        InvestigationBudget budget = new InvestigationBudget(maxSteps, maxToolCalls, maxTokens,
                maxDurationSeconds, usedSteps, usedToolCalls, usedTokens, usedDurationSeconds);
        AuthorityEpoch epoch = new AuthorityEpoch(authorityService, authorityEpoch, authorityActiveFrom);
        return new Investigation(investigationId, incidentId, InvestigationStatus.valueOf(status),
                aggregateVersion, budget, epoch, activeRunId, updatedAt);
    }
}
