/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Low-cardinality publication metrics without event identifiers or content.
 * @author Codex
 * @since 2026-08-25
 */
public final class PublicationObservability {
    private final Counter acknowledged;
    private final Counter retried;
    private final Counter terminal;

    /**
     * Registers backlog, capacity, and bounded outcome meters.
     * @param registry meter registry
     * @param store durable store
     * @param capacity configured capacity
     */
    public PublicationObservability(MeterRegistry registry, PublicationDeliveryPort store, long capacity) {
        Gauge.builder("dpom.publication.backlog", store, PublicationDeliveryPort::pendingCount)
                .description("Active publication intents").register(registry);
        Gauge.builder("dpom.publication.capacity", () -> capacity)
                .description("Configured publication capacity").register(registry);
        acknowledged = registry.counter("dpom.publication.outcome", "outcome", "acknowledged");
        retried = registry.counter("dpom.publication.outcome", "outcome", "retry");
        terminal = registry.counter("dpom.publication.outcome", "outcome", "terminal_failure");
    }

    /**
     * Records a bounded outcome.
     * @param terminalFailure whether retries are exhausted
     * @param success whether broker acknowledgement succeeded
     */
    void record(boolean terminalFailure, boolean success) {
        if (success) {
            acknowledged.increment();
        }
        else if (terminalFailure) {
            terminal.increment();
        }
        else {
            retried.increment();
        }
    }
}
