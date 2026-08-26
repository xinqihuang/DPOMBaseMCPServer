/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

/**
  * 调查领域的稳定失败原因。
  * @author Codex
  * @since 2026-08-25
  */
public enum DiagnosisErrorCode {
    INVALID_TRANSITION,
    STALE_VERSION,
    BUDGET_EXHAUSTED,
    IDEMPOTENCY_CONFLICT,
    EXTERNAL_CALL_STATE_CONFLICT,
    TERMINALIZATION_NOT_ALLOWED;
}
