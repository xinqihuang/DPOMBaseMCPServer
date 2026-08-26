/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;
import java.util.List;

/**
  * 可审计的根因假设及受限证据引用。
  *
  * @param hypothesisId hypothesisId
  * @param investigationId investigationId
  * @param statementCode statementCode
  * @param status status
  * @param evidenceRefs evidenceRefs
  * @param updatedAt updatedAt
  * @author Codex
  * @since 2026-08-25
  */
public record Hypothesis(
        String hypothesisId,
        String investigationId,
        String statementCode,
        HypothesisStatus status,
        List<String> evidenceRefs,
        Instant updatedAt) {
    public Hypothesis {
        hypothesisId = DomainRules.id(hypothesisId, "hypothesisId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        statementCode = DomainRules.code(statementCode, "statementCode");
        status = DomainRules.required(status, "status");
        evidenceRefs = List.copyOf(DomainRules.required(evidenceRefs, "evidenceRefs"));
        if (evidenceRefs.size() > 128) {
            throw new IllegalArgumentException("evidenceRefs");
        }
        evidenceRefs.forEach(value -> DomainRules.id(value, "evidenceRef"));
        updatedAt = DomainRules.required(updatedAt, "updatedAt");
    }
}
