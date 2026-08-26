/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

/**
  * 有界调查预算及已消费计数。
  *
  * @param maxSteps maxSteps
  * @param maxToolCalls maxToolCalls
  * @param maxTokens maxTokens
  * @param maxDurationSeconds maxDurationSeconds
  * @param usedSteps usedSteps
  * @param usedToolCalls usedToolCalls
  * @param usedTokens usedTokens
  * @param usedDurationSeconds usedDurationSeconds
  * @author Codex
  * @since 2026-08-25
  */
public record InvestigationBudget(
        int maxSteps,
        int maxToolCalls,
        long maxTokens,
        long maxDurationSeconds,
        int usedSteps,
        int usedToolCalls,
        long usedTokens,
        long usedDurationSeconds) {
    public InvestigationBudget {
        if (maxSteps < 1
                || maxToolCalls < 1
                || maxTokens < 1L
                || maxDurationSeconds < 1L
                || usedSteps < 0
                || usedToolCalls < 0
                || usedTokens < 0L
                || usedDurationSeconds < 0L
                || usedSteps > maxSteps
                || usedToolCalls > maxToolCalls
                || usedTokens > maxTokens
                || usedDurationSeconds > maxDurationSeconds) {
            throw new IllegalArgumentException("budget");
        }
    }
}
