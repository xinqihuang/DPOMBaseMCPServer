package com.huawei.smartom.agentic.diagnosis;

import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.policy.DiagnosisDomainException;
import com.huawei.smartom.agentic.diagnosis.policy.InvestigationLifecyclePolicy;
import com.huawei.smartom.agentic.diagnosis.port.TerminalCommit;
import com.huawei.smartom.agentic.diagnosis.service.InvestigationTerminalizationService;
import com.huawei.smartom.agentic.diagnosis.service.TerminalizationCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestigationTerminalizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T02:00:00Z");

    @Test
    void eligibleTerminalizationCommitsImmutablePublicationIntent() {
        var committed = new AtomicReference<TerminalCommit>();
        var service = service(commit -> { committed.set(commit); return true; });
        var result = service.terminalize(command(InvestigationStatus.COMPLETED, conclusion()));
        assertThat(result.publicationRequested()).isTrue();
        assertThat(committed.get().publicationIntent()).isNotNull();
        assertThat(committed.get().publicationIntent().aggregateVersion()).isEqualTo(8);
        assertThat(committed.get().investigation().status()).isEqualTo(InvestigationStatus.COMPLETED);
    }

    @Test
    void failedAndCancelledTerminalizationRemainEventFree() {
        var committed = new AtomicReference<TerminalCommit>();
        var service = service(commit -> { committed.set(commit); return true; });
        assertThat(service.terminalize(command(InvestigationStatus.FAILED, null)).publicationRequested()).isFalse();
        assertThat(committed.get().publicationIntent()).isNull();
    }

    @Test
    void transactionRejectionDoesNotReportSuccess() {
        var service = service(commit -> false);
        assertThatThrownBy(() -> service.terminalize(command(InvestigationStatus.COMPLETED, conclusion())))
                .isInstanceOf(DiagnosisDomainException.class);
    }

    private InvestigationTerminalizationService service(
            com.huawei.smartom.agentic.diagnosis.port.InvestigationTransactionPort transactions) {
        var counter = new AtomicInteger();
        return new InvestigationTerminalizationService(new InvestigationLifecyclePolicy(), transactions,
                () -> NOW, namespace -> namespace + "-" + counter.incrementAndGet());
    }

    private TerminalizationCommand command(InvestigationStatus status, Conclusion conclusion) {
        var investigation = new Investigation("INV-1", "INC-1", InvestigationStatus.RUNNING, 7,
                new InvestigationBudget(10, 10, 1000, 60, 1, 1, 10, 5),
                new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", NOW), "RUN-1", NOW);
        return new TerminalizationCommand(investigation, 7, status, conclusion, 1, 5);
    }

    private Conclusion conclusion() {
        return new Conclusion("CON-1", "INV-1", ConclusionType.ROOT_CAUSE_IDENTIFIED,
                "ROOT_CAUSE_CONFIRMED", List.of("EV-1"), NOW);
    }
}
