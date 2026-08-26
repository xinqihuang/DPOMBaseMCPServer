/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.Hypothesis;
import com.huawei.smartom.agentic.diagnosis.model.HypothesisStatus;
import com.huawei.smartom.agentic.diagnosis.model.Incident;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationRun;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosisOnlyReportBuilderTest {
    private static final Instant START = Instant.parse("2026-08-15T03:56:55Z");
    private final DiagnosisOnlyReportBuilder builder = new DiagnosisOnlyReportBuilder();

    @Test void persistedFactsMapToCompleteDiagnosisOnlyProjection() {
        DiagnosticReportDraft report = builder.build(source("EVID-1", List.of()));
        assertThat(report.reportProfile()).isEqualTo("DIAGNOSIS_ONLY");
        assertThat(report.evaluationOutcome()).isEqualTo("NOT_REQUIRED");
        assertThat(report.completeness()).isEqualTo("COMPLETE");
        assertThat(report.conclusions().getFirst().disposition()).isEqualTo("CONFIRMED");
        assertThat(report.recommendations().getFirst().executionState()).isEqualTo("ADVISORY_ONLY");
    }

    @Test void missingCapabilityRemainsIncompleteAndVisible() {
        DiagnosticReportDraft report = builder.build(source("EVID-1", List.of("MISSING_REQUIRED_EVIDENCE")));
        assertThat(report.completeness()).isEqualTo("INCOMPLETE");
        assertThat(report.gapCodes()).containsExactly("MISSING_REQUIRED_EVIDENCE");
    }

    @Test void orphanClaimEvidenceFailsClosed() {
        assertThatThrownBy(() -> builder.build(source("EVID-MISSING", List.of())))
                .hasMessage("REPORT_EVIDENCE_ORPHAN");
    }

    private DiagnosisReportSourceSnapshot source(String conclusionEvidence, List<String> gaps) {
        Incident incident = new Incident("INC-1", "APM_ALARM", START);
        InvestigationBudget budget = new InvestigationBudget(10, 10, 1000, 3600, 3, 3, 200, 60);
        Investigation investigation = new Investigation("INV-1", "INC-1", InvestigationStatus.COMPLETED, 3,
                budget, new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", START), "RUN-1", START.plusSeconds(60));
        InvestigationRun run = new InvestigationRun("RUN-1", "INV-1", 1, InvestigationStatus.COMPLETED, 2,
                START, START.plusSeconds(60));
        var target = new DiagnosticReportDraft.Target("HUAWEI_CLOUD", "cn-north-9", "APM_INSTANCE",
                "instance-2121291", "DPBinMedService");
        var timeline = List.of(new DiagnosticReportDraft.TimelineItem("TIME-1", START, "ALERT", "Alarm active"));
        var observations = List.of(new Observation("OBS-1", "INV-1", "EVID-1", "EDEN_HIGH", START));
        var hypotheses = List.of(new Hypothesis("HYP-1", "INV-1", "TEMPLATE_METRIC_MISMATCH",
                HypothesisStatus.SUPPORTED, List.of("EVID-1"), START.plusSeconds(30)));
        var conclusions = List.of(new Conclusion("CON-1", "INV-1", ConclusionType.ROOT_CAUSE_IDENTIFIED,
                "EDEN_CODECACHE_MISMATCH", List.of(conclusionEvidence), START.plusSeconds(60)));
        var evidence = List.of(new EvidenceRecord("EVID-1", "APM_TREND", "artifact-1", "EDEN_MATCH",
                "a".repeat(64), 128, START.plusSeconds(10)));
        var recommendations = List.of(new DiagnosticReportDraft.Recommendation("REC-1", "REVIEW_TEMPLATE_FILTER",
                List.of("EVID-1"), "HUMAN_REVIEW_REQUIRED", "ADVISORY_ONLY"));
        return new DiagnosisReportSourceSnapshot(incident, investigation, run, target, START.plusSeconds(300),
                timeline, observations, hypotheses, conclusions, evidence, gaps, recommendations, "1.0.0",
                "b".repeat(64), START.plusSeconds(400), Map.of("provider.huawei.apm@1", Map.of("alarmId", "16557989")));
    }
}
