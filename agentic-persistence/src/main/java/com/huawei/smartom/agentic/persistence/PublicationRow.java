/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;

import java.time.Instant;

/**
 * Database projection for a frozen publication intent.
 *
 * @param intentId intent identity
 * @param eventId event identity
 * @param investigationId investigation identity
 * @param topicName fixed topic
 * @param aggregateSequence ordering sequence
 * @param aggregateVersion source version
 * @param authorityEpoch source authority
 * @param canonicalContent frozen bytes
 * @param canonicalSha256 content digest
 * @param createdAt creation time
 * @param attemptCount completed lease count
 * @author Codex
 * @since 2026-08-25
 */
public record PublicationRow(String intentId, String eventId, String investigationId, String topicName,
                             long aggregateSequence, long aggregateVersion, String authorityEpoch,
                             byte[] canonicalContent, String canonicalSha256, Instant createdAt,
                             int attemptCount) {
    FrozenPublication toDomain() {
        return new FrozenPublication(intentId, eventId, investigationId, topicName, aggregateSequence,
                aggregateVersion, authorityEpoch, canonicalContent, canonicalSha256, createdAt);
    }
}
