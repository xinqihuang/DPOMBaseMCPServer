/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;
import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Bounded post-commit worker providing recoverable at-least-once delivery.
 * @author Codex
 * @since 2026-08-25
 */
public final class PublicationWorker {
    private final PublicationDeliveryPort store;
    private final CanonicalPublisher publisher;
    private final PublicationPolicy policy;
    private final Clock clock;
    private final String owner;
    private final PublicationObservability observability;

    /**
     * Creates a publication worker.
     * @param store durable store
     * @param publisher broker adapter
     * @param policy bounded policy
     * @param clock time source
     * @param owner worker identity
     */
    public PublicationWorker(PublicationDeliveryPort store, CanonicalPublisher publisher,
                             PublicationPolicy policy, Clock clock, String owner) {
        this(store, publisher, policy, clock, owner, null);
    }

    /**
     * Creates a publication worker with metrics.
     * @param store durable store
     * @param publisher broker adapter
     * @param policy bounded policy
     * @param clock time source
     * @param owner worker identity
     * @param observability bounded metrics
     */
    public PublicationWorker(PublicationDeliveryPort store, CanonicalPublisher publisher,
                             PublicationPolicy policy, Clock clock, String owner,
                             PublicationObservability observability) {
        this.store = store;
        this.publisher = publisher;
        this.policy = policy;
        this.clock = clock;
        this.owner = owner;
        this.observability = observability;
    }

    /**
     * Attempts at most one bounded lease batch.
     * @return number leased
     */
    public int runOnce() {
        Instant now = clock.instant();
        List<PublicationLease> leases = store.leaseEligible(owner, now, policy.batchSize(),
                policy.leaseDuration(), policy.maxAttempts(), policy.maxAge());
        for (PublicationLease lease : leases) {
            publishOne(lease);
        }
        return leases.size();
    }

    private void publishOne(PublicationLease lease) {
        try {
            publisher.publish(lease);
            store.acknowledge(lease.publication().intentId(), lease.fencingToken(), clock.instant());
            record(false, true);
        }
        catch (RuntimeException exception) {
            boolean terminal = lease.attempt() >= policy.maxAttempts()
                    || lease.publication().createdAt().plus(policy.maxAge()).isBefore(clock.instant());
            Instant retryAt = clock.instant().plus(policy.backoff(lease.attempt()));
            store.recordFailure(lease.publication().intentId(), lease.fencingToken(), retryAt,
                    terminal, terminal ? "DELIVERY_ATTEMPTS_EXHAUSTED" : "TRANSIENT_DELIVERY_FAILURE");
            record(terminal, false);
        }
    }

    private void record(boolean terminal, boolean success) {
        if (observability != null) {
            observability.record(terminal, success);
        }
    }
}
