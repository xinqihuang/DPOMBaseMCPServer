/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.service;

import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;

/**
  * 终态化所需的已验证输入。
  *
  * @param investigation investigation
  * @param expectedVersion expectedVersion
  * @param targetStatus targetStatus
  * @param conclusion conclusion
  * @param aggregateSequence aggregateSequence
  * @param progressSequence progressSequence
  * @author Codex
  * @since 2026-08-25
  */
public record TerminalizationCommand(
        Investigation investigation,
        long expectedVersion,
        InvestigationStatus targetStatus,
        Conclusion conclusion,
        long aggregateSequence,
        long progressSequence) {
    public TerminalizationCommand {
        if (investigation == null
                || targetStatus == null
                || expectedVersion < 1L
                || aggregateSequence < 1L
                || progressSequence < 1L) {
            throw new IllegalArgumentException("terminalization command");
        }
        if (targetStatus.evaluationEligible() && conclusion == null) {
            throw new IllegalArgumentException("conclusion");
        }
    }
}
