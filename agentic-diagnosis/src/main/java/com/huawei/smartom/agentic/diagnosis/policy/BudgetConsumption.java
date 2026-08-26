/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

/**
  * 一次原子预算消费请求。
  *
  * @param steps steps
  * @param toolCalls toolCalls
  * @param tokens tokens
  * @param durationSeconds durationSeconds
  * @author Codex
  * @since 2026-08-25
  */
public record BudgetConsumption(int steps, int toolCalls, long tokens, long durationSeconds) {
    public BudgetConsumption {
        if (steps < 0 || toolCalls < 0 || tokens < 0L || durationSeconds < 0L) {
            throw new IllegalArgumentException("budget consumption");
        }
    }
}
