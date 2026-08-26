/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;
import java.util.List;

/**
  * 终态调查结论及其证据引用。
  *
  * @param conclusionId conclusionId
  * @param investigationId investigationId
  * @param type type
  * @param summaryCode summaryCode
  * @param evidenceRefs evidenceRefs
  * @param concludedAt concludedAt
  * @author Codex
  * @since 2026-08-25
  */
public record Conclusion(
        String conclusionId,
        String investigationId,
        ConclusionType type,
        String summaryCode,
        List<String> evidenceRefs,
        Instant concludedAt) {
    public Conclusion {
        conclusionId = DomainRules.id(conclusionId, "conclusionId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        type = DomainRules.required(type, "type");
        summaryCode = DomainRules.code(summaryCode, "summaryCode");
        evidenceRefs = List.copyOf(DomainRules.required(evidenceRefs, "evidenceRefs"));
        if (evidenceRefs.isEmpty() || evidenceRefs.size() > 128) {
            throw new IllegalArgumentException("evidenceRefs");
        }
        evidenceRefs.forEach(value -> DomainRules.id(value, "evidenceRef"));
        concludedAt = DomainRules.required(concludedAt, "concludedAt");
    }
}
