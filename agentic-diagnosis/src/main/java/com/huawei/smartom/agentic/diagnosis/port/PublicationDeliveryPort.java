/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Durable operations used by the post-commit publication worker.
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface PublicationDeliveryPort {
    /**
     * Persists canonical content once without permitting replacement.
     * @param publication frozen publication
     * @return true only for the first matching freeze
     */
    boolean freeze(FrozenPublication publication);

    /**
     * Acquires eligible or expired intents with fresh fencing tokens.
     * @param owner bounded worker identity
     * @param now lease decision time
     * @param limit maximum leases
     * @param leaseDuration lease duration
     * @param maxAttempts attempt limit
     * @param maxAge record age limit
     * @return acquired leases
     */
    List<PublicationLease> leaseEligible(String owner, Instant now, int limit, Duration leaseDuration,
                                         int maxAttempts, Duration maxAge);

    /**
     * Acknowledges only the currently fenced delivery attempt.
     * @param intentId intent identity
     * @param fencingToken active token
     * @param acknowledgedAt acknowledgement time
     * @return true when the active fence matched
     */
    boolean acknowledge(String intentId, String fencingToken, Instant acknowledgedAt);

    /**
     * Records a retryable or terminal failure for the currently fenced attempt.
     * @param intentId intent identity
     * @param fencingToken active token
     * @param retryAt next eligible time
     * @param terminal whether retry is exhausted
     * @param reasonCode stable bounded reason
     * @return true when the active fence matched
     */
    boolean recordFailure(String intentId, String fencingToken, Instant retryAt, boolean terminal,
                          String reasonCode);

    /**
     * Re-admits original frozen content and appends bounded operator audit.
     * @param intentId intent identity
     * @param operatorRef authenticated operator reference
     * @param reasonCode stable replay reason
     * @param requestedAt request time
     * @return true when an eligible immutable record was admitted
     */
    boolean requestReplay(String intentId, String operatorRef, String reasonCode, Instant requestedAt);

    /**
     * Returns the bounded active backlog.
     * @return pending and leased record count
     */
    long pendingCount();
}
