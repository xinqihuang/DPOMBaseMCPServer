/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import java.util.Arrays;

/**
 * Canonical transport-neutral bytes and their SHA-256 identity.
 * @param bytes canonical bytes
 * @param sha256 lowercase digest
 * @author Codex
 * @since 2026-08-25
 */
public record CanonicalRecord(byte[] bytes, String sha256) {
    public CanonicalRecord {
        bytes = Arrays.copyOf(bytes, bytes.length);
        if (bytes.length == 0 || bytes.length > 65_536 || sha256 == null || sha256.length() != 64) {
            throw new IllegalArgumentException("canonical record bounds");
        }
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
