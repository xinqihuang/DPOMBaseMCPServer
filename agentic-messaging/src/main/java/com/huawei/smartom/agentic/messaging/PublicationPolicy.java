/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import java.time.Duration;

/**
 * Bounded leasing and retry policy.
 * @param batchSize maximum batch
 * @param maxAttempts attempt limit
 * @param maxAge record age limit
 * @param leaseDuration lease duration
 * @param initialBackoff first backoff
 * @param maxBackoff backoff cap
 * @param capacity backlog capacity
 * @author Codex
 * @since 2026-08-25
 */
public record PublicationPolicy(int batchSize, int maxAttempts, Duration maxAge,
                                Duration leaseDuration, Duration initialBackoff,
                                Duration maxBackoff, long capacity) {
    public PublicationPolicy {
        if (batchSize < 1 || batchSize > 1000 || maxAttempts < 1 || maxAttempts > 100
                || maxAge == null || maxAge.isNegative() || maxAge.isZero()
                || leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()
                || maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0
                || capacity < batchSize || capacity > 1_000_000L) {
            throw new IllegalArgumentException("publication policy bounds");
        }
    }

    /** Returns capped exponential backoff without overflowing. */
    public Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 30);
        long millis;
        try {
            millis = Math.multiplyExact(initialBackoff.toMillis(), multiplier);
        }
        catch (ArithmeticException exception) {
            millis = maxBackoff.toMillis();
        }
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }
}
