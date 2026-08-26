/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 从权威状态产生的有界进度记录。
  *
  * @param progressId progressId
  * @param investigationId investigationId
  * @param runId runId
  * @param progressSequence progressSequence
  * @param aggregateVersion aggregateVersion
  * @param status status
  * @param stageCode stageCode
  * @param summaryCode summaryCode
  * @param occurredAt occurredAt
  * @author Codex
  * @since 2026-08-25
  */
public record ProgressRecord(
        String progressId,
        String investigationId,
        String runId,
        long progressSequence,
        long aggregateVersion,
        ProgressStatus status,
        String stageCode,
        String summaryCode,
        Instant occurredAt) {
    public ProgressRecord {
        progressId = DomainRules.id(progressId, "progressId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        runId = DomainRules.id(runId, "runId");
        if (progressSequence < 1L || aggregateVersion < 1L) {
            throw new IllegalArgumentException("progress sequence/version");
        }
        status = DomainRules.required(status, "status");
        stageCode = DomainRules.code(stageCode, "stageCode");
        summaryCode = DomainRules.code(summaryCode, "summaryCode");
        occurredAt = DomainRules.required(occurredAt, "occurredAt");
    }
}
