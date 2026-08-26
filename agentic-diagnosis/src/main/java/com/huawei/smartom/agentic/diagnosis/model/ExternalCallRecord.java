/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 外部调用的幂等身份与不确定性事实。
  *
  * @param callId callId
  * @param investigationId investigationId
  * @param idempotencyKey idempotencyKey
  * @param state state
  * @param attempt attempt
  * @param resultCode resultCode
  * @param updatedAt updatedAt
  * @author Codex
  * @since 2026-08-25
  */
public record ExternalCallRecord(
        String callId,
        String investigationId,
        String idempotencyKey,
        ExternalCallState state,
        int attempt,
        String resultCode,
        Instant updatedAt) {
    public ExternalCallRecord {
        callId = DomainRules.id(callId, "callId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        idempotencyKey = DomainRules.id(idempotencyKey, "idempotencyKey");
        state = DomainRules.required(state, "state");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt");
        }
        if (resultCode != null) {
            resultCode = DomainRules.code(resultCode, "resultCode");
        }
        updatedAt = DomainRules.required(updatedAt, "updatedAt");
    }
}
