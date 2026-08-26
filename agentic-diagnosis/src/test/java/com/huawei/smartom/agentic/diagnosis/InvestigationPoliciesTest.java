package com.huawei.smartom.agentic.diagnosis;

import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.CommandReceipt;
import com.huawei.smartom.agentic.diagnosis.model.ExternalCallRecord;
import com.huawei.smartom.agentic.diagnosis.model.ExternalCallState;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationCheckpoint;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.policy.BudgetConsumption;
import com.huawei.smartom.agentic.diagnosis.policy.DiagnosisDomainException;
import com.huawei.smartom.agentic.diagnosis.policy.DiagnosisErrorCode;
import com.huawei.smartom.agentic.diagnosis.policy.ExternalCallPolicy;
import com.huawei.smartom.agentic.diagnosis.policy.IdempotentCommandOutcome;
import com.huawei.smartom.agentic.diagnosis.policy.IdempotentCommandPolicy;
import com.huawei.smartom.agentic.diagnosis.policy.InvestigationBudgetPolicy;
import com.huawei.smartom.agentic.diagnosis.policy.InvestigationLifecyclePolicy;
import com.huawei.smartom.agentic.diagnosis.policy.ResumeOutcome;
import com.huawei.smartom.agentic.diagnosis.policy.ResumePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestigationPoliciesTest {

    private static final Instant NOW = Instant.parse("2026-08-25T02:00:00Z");

    @Test
    void lifecycleTransitionsAndIncrementsVersion() {
        Investigation current = investigation(InvestigationStatus.ACCEPTED, 1);
        Investigation updated = new InvestigationLifecyclePolicy()
                .transition(current, InvestigationStatus.RUNNING, 1, NOW.plusSeconds(1));
        assertThat(updated.status()).isEqualTo(InvestigationStatus.RUNNING);
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void lifecycleRejectsStaleAndIllegalTransitions() {
        var policy = new InvestigationLifecyclePolicy();
        assertThatThrownBy(() -> policy.transition(investigation(InvestigationStatus.RUNNING, 3),
                InvestigationStatus.COMPLETED, 2, NOW)).isInstanceOf(DiagnosisDomainException.class)
                .extracting("errorCode").isEqualTo(DiagnosisErrorCode.STALE_VERSION);
        assertThatThrownBy(() -> policy.transition(investigation(InvestigationStatus.COMPLETED, 3),
                InvestigationStatus.RUNNING, 3, NOW)).isInstanceOf(DiagnosisDomainException.class)
                .extracting("errorCode").isEqualTo(DiagnosisErrorCode.INVALID_TRANSITION);
    }

    @Test
    void budgetConsumptionIsAtomicAndBounded() {
        var current = budget();
        var policy = new InvestigationBudgetPolicy();
        assertThat(policy.consume(current, new BudgetConsumption(1, 2, 100, 5)).usedToolCalls()).isEqualTo(2);
        assertThatThrownBy(() -> policy.consume(current, new BudgetConsumption(11, 0, 0, 0)))
                .isInstanceOf(DiagnosisDomainException.class)
                .extracting("errorCode").isEqualTo(DiagnosisErrorCode.BUDGET_EXHAUSTED);
        assertThat(current.usedSteps()).isZero();
    }

    @Test
    void commandIdentityBindsOneDigest() {
        var policy = new IdempotentCommandPolicy();
        String first = "a".repeat(64);
        var receipt = new CommandReceipt("CMD-1", "INV-1", first, "ACCEPTED", NOW);
        assertThat(policy.evaluate(null, first)).isEqualTo(IdempotentCommandOutcome.NEW);
        assertThat(policy.evaluate(receipt, first)).isEqualTo(IdempotentCommandOutcome.EQUIVALENT);
        assertThatThrownBy(() -> policy.evaluate(receipt, "b".repeat(64)))
                .isInstanceOf(DiagnosisDomainException.class)
                .extracting("errorCode").isEqualTo(DiagnosisErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void dispatchedTimeoutRequiresReconciliationBeforeRetry() {
        var policy = new ExternalCallPolicy();
        var planned = new ExternalCallRecord("CALL-1", "INV-1", "CALL-IDEM-1",
                ExternalCallState.PLANNED, 0, null, NOW);
        var uncertain = policy.timeoutAfterDispatch(policy.begin(planned, NOW.plusSeconds(1)), NOW.plusSeconds(2));
        assertThat(uncertain.state()).isEqualTo(ExternalCallState.UNCERTAIN);
        assertThatThrownBy(() -> policy.begin(uncertain, NOW.plusSeconds(3)))
                .isInstanceOf(DiagnosisDomainException.class);
        assertThat(policy.reconcile(uncertain, true, NOW.plusSeconds(4)).state())
                .isEqualTo(ExternalCallState.SUCCEEDED);
    }

    @Test
    void resumeReturnsExplicitNonSuccessStates() {
        var policy = new ResumePolicy();
        var current = investigation(InvestigationStatus.RUNNING, 4);
        assertThat(policy.evaluate(current, checkpoint(4, ExternalCallState.SUCCEEDED)))
                .isEqualTo(ResumeOutcome.RESUMABLE);
        assertThat(policy.evaluate(current, checkpoint(3, ExternalCallState.SUCCEEDED)))
                .isEqualTo(ResumeOutcome.STALE_CHECKPOINT);
        assertThat(policy.evaluate(current, checkpoint(4, ExternalCallState.UNCERTAIN)))
                .isEqualTo(ResumeOutcome.MANUAL_RECONCILIATION_REQUIRED);
    }

    private Investigation investigation(InvestigationStatus status, long version) {
        return new Investigation("INV-1", "INC-1", status, version, budget(),
                new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", NOW), "RUN-1", NOW);
    }

    private InvestigationBudget budget() {
        return new InvestigationBudget(10, 20, 1000, 60, 0, 0, 0, 0);
    }

    private InvestigationCheckpoint checkpoint(long version, ExternalCallState state) {
        return new InvestigationCheckpoint("CP-1", "INV-1", "RUN-1", version, 2, state, NOW);
    }
}
