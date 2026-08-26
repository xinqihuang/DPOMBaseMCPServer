/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
  * Investigation 状态机与乐观版本策略。
  * @author Codex
  * @since 2026-08-25
  */
public final class InvestigationLifecyclePolicy {
    private static final Map<InvestigationStatus, Set<InvestigationStatus>> ALLOWED =
            Map.of(
                    InvestigationStatus.ACCEPTED,
                    Set.of(InvestigationStatus.RUNNING, InvestigationStatus.CANCELLED),
                    InvestigationStatus.RUNNING,
                    Set.of(
                            InvestigationStatus.PAUSED,
                            InvestigationStatus.COMPLETED,
                            InvestigationStatus.INCONCLUSIVE,
                            InvestigationStatus.FAILED,
                            InvestigationStatus.CANCELLED),
                    InvestigationStatus.PAUSED,
                    Set.of(InvestigationStatus.RUNNING, InvestigationStatus.CANCELLED));

    /**
     * 在预期版本上执行单向状态转换。
     *
     * @param current 当前聚合
     * @param target 目标状态
     * @param expectedVersion 调用方读取的版本
     * @param now 转换时间
     * @return 版本加一的新聚合
     * @throws DiagnosisDomainException 版本过期或转换非法
     */
    public Investigation transition(
            Investigation current, InvestigationStatus target, long expectedVersion, Instant now) {
        if (current.version() != expectedVersion) {
            throw new DiagnosisDomainException(DiagnosisErrorCode.STALE_VERSION);
        }
        if (!ALLOWED.getOrDefault(current.status(), Set.of()).contains(target)) {
            throw new DiagnosisDomainException(DiagnosisErrorCode.INVALID_TRANSITION);
        }
        return new Investigation(
                current.investigationId(),
                current.incidentId(),
                target,
                current.version() + 1L,
                current.budget(),
                current.authorityEpoch(),
                current.activeRunId(),
                now);
    }
}
