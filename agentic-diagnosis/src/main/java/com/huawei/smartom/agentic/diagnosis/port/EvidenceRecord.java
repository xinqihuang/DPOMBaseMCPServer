/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import java.time.Instant;

/**
  * provider-neutral 的有界证据引用与摘要。
  *
  * @param evidenceId evidenceId
  * @param evidenceType evidenceType
  * @param sourceRef sourceRef
  * @param summaryCode summaryCode
  * @param sha256 sha256
  * @param byteSize byteSize
  * @param capturedAt capturedAt
  * @author Codex
  * @since 2026-08-25
  */
public record EvidenceRecord(
        String evidenceId,
        String evidenceType,
        String sourceRef,
        String summaryCode,
        String sha256,
        long byteSize,
        Instant capturedAt) {
    public EvidenceRecord {
        if (evidenceId == null
                || evidenceId.isBlank()
                || evidenceType == null
                || evidenceType.isBlank()
                || sourceRef == null
                || sourceRef.isBlank()
                || summaryCode == null
                || summaryCode.isBlank()
                || sha256 == null
                || !sha256.matches("[0-9a-f]{64}")
                || byteSize < 1L
                || capturedAt == null) {
            throw new IllegalArgumentException("evidence record");
        }
    }
}
