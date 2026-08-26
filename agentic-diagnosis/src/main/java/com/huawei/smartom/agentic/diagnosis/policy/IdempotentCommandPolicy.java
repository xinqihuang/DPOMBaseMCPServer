/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

import com.huawei.smartom.agentic.diagnosis.model.CommandReceipt;

/**
  * 相同命令身份只能绑定一个 canonical digest。
  * @author Codex
  * @since 2026-08-25
  */
public final class IdempotentCommandPolicy {
    /**
     * 比较已有 receipt 与新摘要。
     *
     * @param existing 已有 receipt，可为空
     * @param canonicalSha256 新命令摘要
     * @return NEW 或 EQUIVALENT
     * @throws DiagnosisDomainException 命令身份与不同摘要冲突
     */
    public IdempotentCommandOutcome evaluate(CommandReceipt existing, String canonicalSha256) {
        if (existing == null) {
            return IdempotentCommandOutcome.NEW;
        }
        if (existing.canonicalSha256().equals(canonicalSha256)) {
            return IdempotentCommandOutcome.EQUIVALENT;
        }
        throw new DiagnosisDomainException(DiagnosisErrorCode.IDEMPOTENCY_CONFLICT);
    }
}
