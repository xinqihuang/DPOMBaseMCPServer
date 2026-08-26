/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.report;

import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.Hypothesis;
import com.huawei.smartom.agentic.diagnosis.model.HypothesisStatus;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRecord;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从持久化调查事实构建确定性的 diagnosis-only 报告。
 *
 * @author Codex
 * @since 2026-08-26
 */
public final class DiagnosisOnlyReportBuilder {
    /**
     * 构建报告并在确认结论无证据或引用孤立时失败关闭。
     *
     * @param source 冻结事实
     * @return 诊断报告草稿
     */
    public DiagnosticReportDraft build(DiagnosisReportSourceSnapshot source) {
        validateIdentity(source);
        List<DiagnosticReportDraft.EvidenceReference> evidence = evidence(source);
        Set<String> evidenceIds = new HashSet<>();
        evidence.forEach(item -> evidenceIds.add(item.evidenceId()));
        List<DiagnosticReportDraft.Claim> observations = source.observations().stream()
                .sorted(Comparator.comparing(Observation::observationId))
                .map(item -> observation(item, evidenceIds)).toList();
        List<DiagnosticReportDraft.Claim> hypotheses = source.hypotheses().stream()
                .sorted(Comparator.comparing(Hypothesis::hypothesisId))
                .map(item -> hypothesis(item, evidenceIds)).toList();
        List<DiagnosticReportDraft.Claim> conclusions = source.conclusions().stream()
                .sorted(Comparator.comparing(Conclusion::conclusionId))
                .map(item -> conclusion(item, evidenceIds)).toList();
        List<String> gaps = source.gapCodes().stream().distinct().sorted().toList();
        String completeness = gaps.isEmpty() ? "COMPLETE" : "INCOMPLETE";
        return draft(source, observations, hypotheses, conclusions, evidence, gaps, completeness);
    }

    private DiagnosticReportDraft draft(DiagnosisReportSourceSnapshot source,
            List<DiagnosticReportDraft.Claim> observations, List<DiagnosticReportDraft.Claim> hypotheses,
            List<DiagnosticReportDraft.Claim> conclusions,
            List<DiagnosticReportDraft.EvidenceReference> evidence, List<String> gaps, String completeness) {
        return new DiagnosticReportDraft("DIAGNOSIS_ONLY",
                new DiagnosticReportDraft.Identity(source.incident().incidentId(),
                        source.investigation().investigationId(), source.run().runId()),
                source.target(), new DiagnosticReportDraft.Window(source.incident().detectedAt(), source.incidentTo()),
                source.timeline(), observations, hypotheses, conclusions, evidence, gaps, source.recommendations(),
                "NOT_REQUIRED", List.of(new DiagnosticReportDraft.Component("DPOMBaseMCPServer",
                        source.componentVersion(), "diagnosis-event-v2", source.sourceDigest())),
                source.generatedAt(), completeness, source.extensions());
    }

    private List<DiagnosticReportDraft.EvidenceReference> evidence(DiagnosisReportSourceSnapshot source) {
        return source.evidence().stream().sorted(Comparator.comparing(EvidenceRecord::evidenceId))
                .map(item -> new DiagnosticReportDraft.EvidenceReference(item.evidenceId(), item.evidenceType(),
                        "dpom-evidence-port-v1", item.sourceRef(), item.sha256(), item.capturedAt(),
                        new DiagnosticReportDraft.Window(source.incident().detectedAt(), source.incidentTo()),
                        source.target().resourceId(), "CONTROLLED", "NONE")).toList();
    }

    private DiagnosticReportDraft.Claim observation(Observation item, Set<String> evidence) {
        require(evidence.contains(item.evidenceRef()), "REPORT_EVIDENCE_ORPHAN");
        return new DiagnosticReportDraft.Claim(item.observationId(), "OBSERVATION", item.summaryCode(), 10000,
                List.of(item.evidenceRef()), List.of(), null);
    }

    private DiagnosticReportDraft.Claim hypothesis(Hypothesis item, Set<String> evidence) {
        item.evidenceRefs().forEach(ref -> require(evidence.contains(ref), "REPORT_EVIDENCE_ORPHAN"));
        int confidence = item.status() == HypothesisStatus.SUPPORTED ? 8000 : 4000;
        return new DiagnosticReportDraft.Claim(item.hypothesisId(), "HYPOTHESIS", item.statementCode(), confidence,
                item.evidenceRefs(), List.of(), "HYPOTHESIS");
    }

    private DiagnosticReportDraft.Claim conclusion(Conclusion item, Set<String> evidence) {
        item.evidenceRefs().forEach(ref -> require(evidence.contains(ref), "REPORT_EVIDENCE_ORPHAN"));
        boolean confirmed = item.type() == ConclusionType.ROOT_CAUSE_IDENTIFIED;
        require(!confirmed || !item.evidenceRefs().isEmpty(), "REPORT_CONFIRMED_UNSUPPORTED");
        return new DiagnosticReportDraft.Claim(item.conclusionId(), "CONCLUSION", item.summaryCode(),
                confirmed ? 9500 : 0, item.evidenceRefs(), List.of(), confirmed ? "CONFIRMED" : "UNDETERMINED");
    }

    private void validateIdentity(DiagnosisReportSourceSnapshot source) {
        require(source.incident().incidentId().equals(source.investigation().incidentId()),
                "REPORT_INCIDENT_LINEAGE_MISMATCH");
        require(source.investigation().investigationId().equals(source.run().investigationId()),
                "REPORT_RUN_LINEAGE_MISMATCH");
        require(!source.incidentTo().isBefore(source.incident().detectedAt()), "REPORT_WINDOW_INVALID");
        require(source.sourceDigest().matches("[0-9a-f]{64}"), "REPORT_SOURCE_DIGEST_INVALID");
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalArgumentException(reason);
        }
    }
}
