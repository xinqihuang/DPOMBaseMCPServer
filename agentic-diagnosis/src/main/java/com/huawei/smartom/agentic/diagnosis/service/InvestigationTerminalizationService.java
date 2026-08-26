/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.service;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressStatus;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;
import com.huawei.smartom.agentic.diagnosis.policy.DiagnosisDomainException;
import com.huawei.smartom.agentic.diagnosis.policy.DiagnosisErrorCode;
import com.huawei.smartom.agentic.diagnosis.policy.InvestigationLifecyclePolicy;
import com.huawei.smartom.agentic.diagnosis.port.ClockPort;
import com.huawei.smartom.agentic.diagnosis.port.IdPort;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationTransactionPort;
import com.huawei.smartom.agentic.diagnosis.port.TerminalCommit;
import java.time.Instant;

/**
  * 可评价终态与 publication intent 的原子领域服务。
  * @author Codex
  * @since 2026-08-25
  */
public final class InvestigationTerminalizationService {
    private final InvestigationLifecyclePolicy lifecycle;
    private final InvestigationTransactionPort transactions;
    private final ClockPort clock;
    private final IdPort ids;

    /**
     * 创建终态化领域服务。
     *
     * @param lifecycle 生命周期策略
     * @param transactions 原子事务端口
     * @param clock 领域时钟
     * @param ids 身份生成端口
     */
    public InvestigationTerminalizationService(
            InvestigationLifecyclePolicy lifecycle,
            InvestigationTransactionPort transactions,
            ClockPort clock,
            IdPort ids) {
        this.lifecycle = lifecycle;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * 原子提交终态；只有 COMPLETED 或 INCONCLUSIVE 请求评价事件。
     *
     * @param command 终态命令
     * @return 新聚合与是否请求发布
     * @throws DiagnosisDomainException 终态非法、版本过期或原子提交失败
     */
    public TerminalizationResult terminalize(TerminalizationCommand command) {
        if (!command.targetStatus().terminal()) {
            throw new DiagnosisDomainException(DiagnosisErrorCode.TERMINALIZATION_NOT_ALLOWED);
        }
        Instant now = this.clock.now();
        Investigation updated =
                this.lifecycle.transition(
                        command.investigation(), command.targetStatus(), command.expectedVersion(), now);
        PublicationIntentRequest intent = this.intent(command, updated, now);
        ProgressRecord progress = this.progress(command, updated, now);
        AuditRecord audit =
                new AuditRecord(
                        this.ids.next("AUD"),
                        updated.investigationId(),
                        "TERMINALIZE",
                        command.targetStatus().name(),
                        updated.version(),
                        now);
        TerminalCommit commit =
                new TerminalCommit(
                        updated, command.expectedVersion(), command.conclusion(), progress, audit, intent);
        if (!this.transactions.commitTerminal(commit)) {
            throw new DiagnosisDomainException(DiagnosisErrorCode.STALE_VERSION);
        }
        return new TerminalizationResult(updated, intent != null);
    }

    private PublicationIntentRequest intent(
            TerminalizationCommand command, Investigation updated, Instant now) {
        if (!command.targetStatus().evaluationEligible()) {
            return null;
        }
        return new PublicationIntentRequest(
                this.ids.next("PUB"),
                this.ids.next("EVT"),
                updated.investigationId(),
                updated.activeRunId(),
                updated.version(),
                command.aggregateSequence(),
                updated.authorityEpoch(),
                now);
    }

    private ProgressRecord progress(
            TerminalizationCommand command, Investigation updated, Instant now) {
        ProgressStatus status = ProgressStatus.valueOf(command.targetStatus().name());
        return new ProgressRecord(
                this.ids.next("PRG"),
                updated.investigationId(),
                updated.activeRunId(),
                command.progressSequence(),
                updated.version(),
                status,
                "TERMINALIZATION",
                command.targetStatus().name(),
                now);
    }
}
