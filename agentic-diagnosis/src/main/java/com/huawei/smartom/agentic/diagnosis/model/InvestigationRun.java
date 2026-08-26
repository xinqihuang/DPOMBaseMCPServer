/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 一次可恢复的 Investigation Run。
  *
  * @param runId runId
  * @param investigationId investigationId
  * @param attempt attempt
  * @param status status
  * @param version version
  * @param startedAt startedAt
  * @param finishedAt finishedAt
  * @author Codex
  * @since 2026-08-25
  */
public record InvestigationRun(
        String runId,
        String investigationId,
        int attempt,
        InvestigationStatus status,
        long version,
        Instant startedAt,
        Instant finishedAt) {
    public InvestigationRun {
        runId = DomainRules.id(runId, "runId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        if (attempt < 1 || version < 1L) {
            throw new IllegalArgumentException("attempt/version");
        }
        status = DomainRules.required(status, "status");
        startedAt = DomainRules.required(startedAt, "startedAt");
        if (status.terminal() && finishedAt == null) {
            throw new IllegalArgumentException("finishedAt");
        }
    }
}
