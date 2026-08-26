/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

/**
 * 受控 Artifact store 返回的 provider-neutral 元数据。
 *
 * @param sourceRef 受控 Artifact 引用
 * @param sha256 Artifact 内容摘要
 * @param byteSize Artifact 字节数
 * @author Codex
 * @since 2026-08-25
 */
public record StoredEvidence(String sourceRef, String sha256, long byteSize) {
    public StoredEvidence {
        if (sourceRef == null || sourceRef.isBlank() || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                || byteSize < 1) {
            throw new IllegalArgumentException("stored evidence");
        }
    }
}
