/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

import com.huawei.smartom.agentic.diagnosis.model.ExternalCallState;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationCheckpoint;

/**
  * 重启恢复时以明确非成功状态替代猜测。
  * @author Codex
  * @since 2026-08-25
  */
public final class ResumePolicy {
    /**
     * 裁决 checkpoint 是否可直接恢复。
     *
     * @param investigation 当前权威聚合
     * @param checkpoint checkpoint
     * @return 恢复裁决
     */
    public ResumeOutcome evaluate(Investigation investigation, InvestigationCheckpoint checkpoint) {
        if (investigation.status().terminal()) {
            return ResumeOutcome.ALREADY_TERMINAL;
        }
        if (checkpoint.aggregateVersion() != investigation.version()) {
            return ResumeOutcome.STALE_CHECKPOINT;
        }
        if (checkpoint.externalCallState() == ExternalCallState.IN_FLIGHT
                || checkpoint.externalCallState() == ExternalCallState.UNCERTAIN) {
            return ResumeOutcome.MANUAL_RECONCILIATION_REQUIRED;
        }
        return ResumeOutcome.RESUMABLE;
    }
}
