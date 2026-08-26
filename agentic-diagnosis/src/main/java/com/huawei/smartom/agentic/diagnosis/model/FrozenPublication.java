/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;
import java.util.Arrays;

/**
 * Immutable canonical publication content persisted before delivery.
 *
 * @param intentId publication intent identity
 * @param eventId canonical event identity
 * @param investigationId partition identity
 * @param topic fixed contract topic
 * @param sequence aggregate or progress sequence
 * @param aggregateVersion persisted aggregate version
 * @param authorityEpoch persisted authority epoch
 * @param canonicalBytes RFC 8785 canonical bytes
 * @param canonicalSha256 lowercase SHA-256 digest
 * @param createdAt durable creation time
 * @author Codex
 * @since 2026-08-25
 */
public record FrozenPublication(String intentId, String eventId, String investigationId, String topic,
                                long sequence, long aggregateVersion, String authorityEpoch,
                                byte[] canonicalBytes, String canonicalSha256, Instant createdAt) {
    public FrozenPublication {
        intentId = DomainRules.id(intentId, "intentId");
        eventId = DomainRules.id(eventId, "eventId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        topic = DomainRules.required(topic, "topic");
        authorityEpoch = DomainRules.id(authorityEpoch, "authorityEpoch");
        canonicalSha256 = DomainRules.required(canonicalSha256, "canonicalSha256");
        createdAt = DomainRules.required(createdAt, "createdAt");
        if (sequence < 1L || aggregateVersion < 1L || canonicalSha256.length() != 64) {
            throw new IllegalArgumentException("publication sequence/version/digest");
        }
        canonicalBytes = Arrays.copyOf(DomainRules.required(canonicalBytes, "canonicalBytes"),
                canonicalBytes.length);
        if (canonicalBytes.length == 0 || canonicalBytes.length > 65_536) {
            throw new IllegalArgumentException("canonicalBytes");
        }
    }

    @Override
    public byte[] canonicalBytes() {
        return Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }
}
