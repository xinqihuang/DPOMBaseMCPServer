/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Capacity-aware publication readiness with bounded details.
 * @author Codex
 * @since 2026-08-25
 */
public final class PublicationReadiness implements HealthIndicator {
    private final PublicationDeliveryPort store;
    private final long capacity;

    /**
     * Creates readiness.
     * @param store durable store
     * @param capacity configured capacity
     */
    public PublicationReadiness(PublicationDeliveryPort store, long capacity) {
        this.store = store;
        this.capacity = capacity;
    }

    @Override
    public Health health() {
        long backlog = store.pendingCount();
        Health.Builder builder = backlog >= capacity ? Health.outOfService() : Health.up();
        return builder.withDetail("state", backlog >= capacity ? "CAPACITY_EXHAUSTED" : "READY")
                .withDetail("backlog", backlog).withDetail("capacity", capacity).build();
    }
}
