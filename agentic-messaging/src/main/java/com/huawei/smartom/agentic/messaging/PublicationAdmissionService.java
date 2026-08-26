/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;

/**
 * Capacity-bounded admission for freezing canonical publication content.
 * @author Codex
 * @since 2026-08-25
 */
public final class PublicationAdmissionService {
    private final PublicationDeliveryPort store;
    private final long capacity;

    /**
     * Creates admission service.
     * @param store durable store
     * @param capacity backlog capacity
     */
    public PublicationAdmissionService(PublicationDeliveryPort store, long capacity) {
        this.store = store;
        this.capacity = capacity;
    }

    /**
     * Freezes a record without replacing an existing identity or exceeding capacity.
     * @param publication frozen canonical record
     * @return stable admission outcome
     */
    public PublicationAdmissionOutcome admit(FrozenPublication publication) {
        if (store.pendingCount() >= capacity) {
            return PublicationAdmissionOutcome.CAPACITY_EXHAUSTED;
        }
        return store.freeze(publication) ? PublicationAdmissionOutcome.ACCEPTED
                : PublicationAdmissionOutcome.IMMUTABLE_CONFLICT;
    }
}
