/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 不可变 Incident 身份与发现事实。
  *
  * @param incidentId incidentId
  * @param titleCode titleCode
  * @param detectedAt detectedAt
  * @author Codex
  * @since 2026-08-25
  */
public record Incident(String incidentId, String titleCode, Instant detectedAt) {
    public Incident {
        incidentId = DomainRules.id(incidentId, "incidentId");
        titleCode = DomainRules.code(titleCode, "titleCode");
        detectedAt = DomainRules.required(detectedAt, "detectedAt");
    }
}
