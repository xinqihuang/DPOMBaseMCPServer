/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
 * Fenced delivery lease carrying frozen canonical content.
 *
 * @param publication frozen publication
 * @param fencingToken single lease fencing token
 * @param attempt bounded attempt number
 * @param leasedUntil expiry time
 * @author Codex
 * @since 2026-08-25
 */
public record PublicationLease(FrozenPublication publication, String fencingToken, int attempt,
                               Instant leasedUntil) {
    public PublicationLease {
        publication = DomainRules.required(publication, "publication");
        fencingToken = DomainRules.id(fencingToken, "fencingToken");
        leasedUntil = DomainRules.required(leasedUntil, "leasedUntil");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt");
        }
    }
}
