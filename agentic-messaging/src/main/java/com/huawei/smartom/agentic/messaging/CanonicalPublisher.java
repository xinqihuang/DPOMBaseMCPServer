/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;

/**
 * Outbound broker boundary for one frozen publication.
 * @author Codex
 * @since 2026-08-25
 */
public interface CanonicalPublisher {
    /**
     * Publishes frozen canonical bytes.
     * @param lease fenced lease
     */
    void publish(PublicationLease lease);
}
