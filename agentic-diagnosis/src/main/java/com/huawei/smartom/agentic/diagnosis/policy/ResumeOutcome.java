/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

/**
  * checkpoint 恢复裁决。
  * @author Codex
  * @since 2026-08-25
  */
public enum ResumeOutcome {
    RESUMABLE,
    STALE_CHECKPOINT,
    MANUAL_RECONCILIATION_REQUIRED,
    ALREADY_TERMINAL;
}
