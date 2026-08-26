/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;

import java.time.Clock;

/**
 * Authenticated immutable replay admission.
 * @author Codex
 * @since 2026-08-25
 */
public final class OperatorReplayService {
    private final PublicationDeliveryPort store;
    private final Clock clock;

    /**
     * Creates replay admission.
     * @param store durable store
     * @param clock time source
     */
    public OperatorReplayService(PublicationDeliveryPort store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Requests replay without accepting replacement identity or content.
     * @param authenticated authentication result
     * @param intentId original intent identity
     * @param operatorRef bounded operator reference
     * @param reasonCode stable reason
     * @return true when admitted
     * @throws SecurityException when unauthenticated
     * @throws IllegalArgumentException when audit fields are invalid
     */
    public boolean request(boolean authenticated, String intentId, String operatorRef, String reasonCode) {
        if (!authenticated) {
            throw new SecurityException("operator authentication required");
        }
        if (!bounded(operatorRef, 128) || !bounded(reasonCode, 64)
                || !reasonCode.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("replay audit fields");
        }
        return store.requestReplay(intentId, operatorRef, reasonCode, clock.instant());
    }

    private static boolean bounded(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum;
    }
}
