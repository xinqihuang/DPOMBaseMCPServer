/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 不含正文和凭据的追加式审计记录。
  *
  * @param auditId auditId
  * @param investigationId investigationId
  * @param actionCode actionCode
  * @param outcomeCode outcomeCode
  * @param aggregateVersion aggregateVersion
  * @param occurredAt occurredAt
  * @author Codex
  * @since 2026-08-25
  */
public record AuditRecord(
        String auditId,
        String investigationId,
        String actionCode,
        String outcomeCode,
        long aggregateVersion,
        Instant occurredAt) {
    public AuditRecord {
        auditId = DomainRules.id(auditId, "auditId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        actionCode = DomainRules.code(actionCode, "actionCode");
        outcomeCode = DomainRules.code(outcomeCode, "outcomeCode");
        if (aggregateVersion < 1L) {
            throw new IllegalArgumentException("aggregateVersion");
        }
        occurredAt = DomainRules.required(occurredAt, "occurredAt");
    }
}
