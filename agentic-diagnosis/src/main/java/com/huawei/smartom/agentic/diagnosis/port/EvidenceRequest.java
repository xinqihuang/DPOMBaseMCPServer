/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import java.time.Instant;

/**
  * 不含 provider DTO 的有界证据请求。
  *
  * @param investigationId investigationId
  * @param serviceCode serviceCode
  * @param from from
  * @param to to
  * @param maxItems maxItems
  * @param evidenceType evidenceType
  * @author Codex
  * @since 2026-08-25
  */
public record EvidenceRequest(
        String investigationId,
        String serviceCode,
        Instant from,
        Instant to,
        int maxItems,
        String evidenceType) {
    public EvidenceRequest {
        if (investigationId == null
                || investigationId.isBlank()
                || serviceCode == null
                || serviceCode.isBlank()
                || from == null
                || to == null
                || from.isAfter(to)
                || maxItems < 1
                || maxItems > 200
                || evidenceType == null
                || evidenceType.isBlank()) {
            throw new IllegalArgumentException("evidence request");
        }
    }
}
