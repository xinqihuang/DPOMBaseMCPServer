/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 对受限证据的不可变观察，不包含证据正文。
  *
  * @param observationId observationId
  * @param investigationId investigationId
  * @param evidenceRef evidenceRef
  * @param summaryCode summaryCode
  * @param observedAt observedAt
  * @author Codex
  * @since 2026-08-25
  */
public record Observation(
        String observationId,
        String investigationId,
        String evidenceRef,
        String summaryCode,
        Instant observedAt) {
    public Observation {
        observationId = DomainRules.id(observationId, "observationId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        evidenceRef = DomainRules.id(evidenceRef, "evidenceRef");
        summaryCode = DomainRules.code(summaryCode, "summaryCode");
        observedAt = DomainRules.required(observedAt, "observedAt");
    }
}
