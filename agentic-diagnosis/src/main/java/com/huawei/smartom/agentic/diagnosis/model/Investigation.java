/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 权威 Investigation 聚合头。
  *
  * @param investigationId investigationId
  * @param incidentId incidentId
  * @param status status
  * @param version version
  * @param budget budget
  * @param authorityEpoch authorityEpoch
  * @param activeRunId activeRunId
  * @param updatedAt updatedAt
  * @author Codex
  * @since 2026-08-25
  */
public record Investigation(
        String investigationId,
        String incidentId,
        InvestigationStatus status,
        long version,
        InvestigationBudget budget,
        AuthorityEpoch authorityEpoch,
        String activeRunId,
        Instant updatedAt) {
    public Investigation {
        investigationId = DomainRules.id(investigationId, "investigationId");
        incidentId = DomainRules.id(incidentId, "incidentId");
        status = DomainRules.required(status, "status");
        if (version < 1L) {
            throw new IllegalArgumentException("version");
        }
        budget = DomainRules.required(budget, "budget");
        authorityEpoch = DomainRules.required(authorityEpoch, "authorityEpoch");
        if (activeRunId != null) {
            activeRunId = DomainRules.id(activeRunId, "activeRunId");
        }
        updatedAt = DomainRules.required(updatedAt, "updatedAt");
    }
}
