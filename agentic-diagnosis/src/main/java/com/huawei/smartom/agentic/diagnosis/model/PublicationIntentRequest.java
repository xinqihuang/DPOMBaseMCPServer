/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 可评价终态产生的不可变 Diagnosis Event publication intent。
  *
  * @param intentId intentId
  * @param eventId eventId
  * @param investigationId investigationId
  * @param runId runId
  * @param aggregateVersion aggregateVersion
  * @param aggregateSequence aggregateSequence
  * @param authorityEpoch authorityEpoch
  * @param createdAt createdAt
  * @author Codex
  * @since 2026-08-25
  */
public record PublicationIntentRequest(
        String intentId,
        String eventId,
        String investigationId,
        String runId,
        long aggregateVersion,
        long aggregateSequence,
        AuthorityEpoch authorityEpoch,
        Instant createdAt) {
    public PublicationIntentRequest {
        intentId = DomainRules.id(intentId, "intentId");
        eventId = DomainRules.id(eventId, "eventId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        runId = DomainRules.id(runId, "runId");
        if (aggregateVersion < 1L || aggregateSequence < 1L) {
            throw new IllegalArgumentException("publication version/sequence");
        }
        authorityEpoch = DomainRules.required(authorityEpoch, "authorityEpoch");
        createdAt = DomainRules.required(createdAt, "createdAt");
    }
}
