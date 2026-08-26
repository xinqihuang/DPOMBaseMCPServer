/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 幂等命令的稳定摘要与首次处理事实。
  *
  * @param commandId commandId
  * @param investigationId investigationId
  * @param canonicalSha256 canonicalSha256
  * @param outcomeCode outcomeCode
  * @param recordedAt recordedAt
  * @author Codex
  * @since 2026-08-25
  */
public record CommandReceipt(
        String commandId,
        String investigationId,
        String canonicalSha256,
        String outcomeCode,
        Instant recordedAt) {
    public CommandReceipt {
        commandId = DomainRules.id(commandId, "commandId");
        investigationId = DomainRules.id(investigationId, "investigationId");
        if (canonicalSha256 == null || !canonicalSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("canonicalSha256");
        }
        outcomeCode = DomainRules.code(outcomeCode, "outcomeCode");
        recordedAt = DomainRules.required(recordedAt, "recordedAt");
    }
}
