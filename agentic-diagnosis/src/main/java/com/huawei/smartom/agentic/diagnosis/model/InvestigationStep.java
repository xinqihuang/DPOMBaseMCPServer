/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 调查步骤的稳定顺序与状态事实。
  *
  * @param stepId stepId
  * @param runId runId
  * @param stepSequence stepSequence
  * @param stepType stepType
  * @param outcomeCode outcomeCode
  * @param recordedAt recordedAt
  * @author Codex
  * @since 2026-08-25
  */
public record InvestigationStep(
        String stepId,
        String runId,
        int stepSequence,
        String stepType,
        String outcomeCode,
        Instant recordedAt) {
    public InvestigationStep {
        stepId = DomainRules.id(stepId, "stepId");
        runId = DomainRules.id(runId, "runId");
        if (stepSequence < 1) {
            throw new IllegalArgumentException("stepSequence");
        }
        stepType = DomainRules.code(stepType, "stepType");
        if (outcomeCode != null) {
            outcomeCode = DomainRules.code(outcomeCode, "outcomeCode");
        }
        recordedAt = DomainRules.required(recordedAt, "recordedAt");
    }
}
