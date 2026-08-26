/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;

/**
  * 一个本地事务中必须同时提交的终态事实。
  *
  * @param investigation investigation
  * @param expectedVersion expectedVersion
  * @param conclusion conclusion
  * @param progress progress
  * @param audit audit
  * @param publicationIntent publicationIntent
  * @author Codex
  * @since 2026-08-25
  */
public record TerminalCommit(
        Investigation investigation,
        long expectedVersion,
        Conclusion conclusion,
        ProgressRecord progress,
        AuditRecord audit,
        PublicationIntentRequest publicationIntent) {
    public TerminalCommit {
        if (investigation == null
                || progress == null
                || audit == null
                || expectedVersion < 1L
                || investigation.status().evaluationEligible() && conclusion == null) {
            throw new IllegalArgumentException("terminal commit");
        }
    }
}
