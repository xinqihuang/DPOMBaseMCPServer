/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;

/**
  * 调查预算的 fail-closed 消费策略。
  * @author Codex
  * @since 2026-08-25
  */
public final class InvestigationBudgetPolicy {
    /**
     * 原子计算消费后的预算；任何维度越界都不返回部分结果。
     *
     * @param budget 当前预算
     * @param consumption 本次消费
     * @return 消费后的预算
     * @throws DiagnosisDomainException 任一预算维度耗尽
     */
    public InvestigationBudget consume(InvestigationBudget budget, BudgetConsumption consumption) {
        int steps = Math.addExact(budget.usedSteps(), consumption.steps());
        int calls = Math.addExact(budget.usedToolCalls(), consumption.toolCalls());
        long tokens = Math.addExact(budget.usedTokens(), consumption.tokens());
        long duration = Math.addExact(budget.usedDurationSeconds(), consumption.durationSeconds());
        if (steps > budget.maxSteps()
                || calls > budget.maxToolCalls()
                || tokens > budget.maxTokens()
                || duration > budget.maxDurationSeconds()) {
            throw new DiagnosisDomainException(DiagnosisErrorCode.BUDGET_EXHAUSTED);
        }
        return new InvestigationBudget(
                budget.maxSteps(),
                budget.maxToolCalls(),
                budget.maxTokens(),
                budget.maxDurationSeconds(),
                steps,
                calls,
                tokens,
                duration);
    }
}
