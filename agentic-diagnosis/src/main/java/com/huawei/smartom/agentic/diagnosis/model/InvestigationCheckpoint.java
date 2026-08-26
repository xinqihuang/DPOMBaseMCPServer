/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 重启恢复所需的版本化 checkpoint。
  *
  * @param checkpointId checkpointId
  * @param investigationId investigationId
  * @param runId runId
  * @param aggregateVersion aggregateVersion
  * @param nextStepSequence nextStepSequence
  * @param externalCallState externalCallState
  * @param createdAt createdAt
  * @author Codex
  * @since 2026-08-25
  */
public record InvestigationCheckpoint(
        String checkpointId,
        String investigationId,
        String runId,
        long aggregateVersion,
        int nextStepSequence,
        ExternalCallState externalCallState,
        Instant createdAt) {
    public InvestigationCheckpoint {
        checkpointId = DomainRules.id(checkpointId, "checkpointId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        runId = DomainRules.id(runId, "runId");
        if (aggregateVersion < 1L || nextStepSequence < 1) {
            throw new IllegalArgumentException("checkpoint version/sequence");
        }
        externalCallState = DomainRules.required(externalCallState, "externalCallState");
        createdAt = DomainRules.required(createdAt, "createdAt");
    }
}
