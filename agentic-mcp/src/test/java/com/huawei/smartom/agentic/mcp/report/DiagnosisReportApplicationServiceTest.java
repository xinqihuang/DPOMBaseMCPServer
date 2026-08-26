/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.huawei.smartom.agentic.diagnosis.port.DiagnosisReportSourceAuthority;
import com.huawei.smartom.agentic.diagnosis.port.DiagnosticReportRepository;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRecord;
import com.huawei.smartom.agentic.diagnosis.report.DiagnosisReportSourceSnapshot;
import com.huawei.smartom.agentic.diagnosis.report.DiagnosticReportDraft;
import com.huawei.smartom.agentic.diagnosis.report.PublishedDiagnosticReport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** diagnosis-only 应用服务的幂等、修订、重放和关闭测试。 */
class DiagnosisReportApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");

    @Test void generatesIdempotentlyReplaysAndSupersedesWithoutMutation() {
        MemoryRepository repository = new MemoryRepository();
        DiagnosisReportApplicationService service = service(repository, source("a".repeat(64)), true, true);
        var firstCommand = new DiagnosisReportApplicationService.GenerateCommand("REQ-1", "INV-1", 0, null, "operator");
        PublishedDiagnosticReport first = service.generate(firstCommand);
        assertThat(service.generate(firstCommand).reportId()).isEqualTo(first.reportId());
        assertThat(service.replay(first.reportId()).path("reportDigest").asText()).isEqualTo(first.reportDigest());
        var secondCommand = new DiagnosisReportApplicationService.GenerateCommand("REQ-2", "INV-1", 1,
                "ALARM_LIFECYCLE_RECOVERED", "operator");
        PublishedDiagnosticReport second = service.generate(secondCommand);
        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(second.supersedesReportId()).isEqualTo(first.reportId());
        assertThat(service.page("INV-1", null, 10)).extracting(PublishedDiagnosticReport::revisionNumber)
                .containsExactly(2L, 1L);
    }

    @Test void staleRevisionConflictingRequestAndDefaultOffFailClosed() {
        MemoryRepository repository = new MemoryRepository();
        DiagnosisReportApplicationService enabled = service(repository, source("a".repeat(64)), true, true);
        enabled.generate(new DiagnosisReportApplicationService.GenerateCommand("REQ-1", "INV-1", 0, null, "operator"));
        assertThatThrownBy(() -> enabled.generate(new DiagnosisReportApplicationService.GenerateCommand(
                "REQ-2", "INV-1", 0, null, "operator"))).hasMessage("REPORT_REVISION_STALE");
        DiagnosisReportApplicationService changed = service(repository, source("b".repeat(64)), true, true);
        assertThatThrownBy(() -> changed.generate(new DiagnosisReportApplicationService.GenerateCommand(
                "REQ-1", "INV-1", 1, "SOURCE_UPDATED", "operator")))
                .hasMessage("REPORT_REQUEST_DIGEST_CONFLICT");
        DiagnosisReportApplicationService disabled = service(new MemoryRepository(), source("a".repeat(64)), false, false);
        assertThatThrownBy(() -> disabled.generate(new DiagnosisReportApplicationService.GenerateCommand(
                "REQ-OFF", "INV-1", 0, null, "operator"))).hasMessage("REPORT_GENERATION_DISABLED");
    }

    private DiagnosisReportApplicationService service(MemoryRepository repository,
            DiagnosisReportSourceSnapshot source, boolean generation, boolean reads) {
        DiagnosisReportSourceAuthority authority = investigationId -> source;
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        return new DiagnosisReportApplicationService(authority, repository,
                new DiagnosticReportApiProperties(generation, reads, "token"), json,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DiagnosisReportSourceSnapshot source(String sourceDigest) {
        Instant start = Instant.parse("2026-08-15T03:56:55Z");
        Incident incident = new Incident("INC-1", "APM_ALARM", start);
        var budget = new InvestigationBudget(10, 10, 1000, 3600, 3, 3, 200, 60);
        Investigation investigation = new Investigation("INV-1", "INC-1", InvestigationStatus.COMPLETED, 3,
                budget, new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", start), "RUN-1", start.plusSeconds(60));
        InvestigationRun run = new InvestigationRun("RUN-1", "INV-1", 1, InvestigationStatus.COMPLETED, 2,
                start, start.plusSeconds(60));
        var target = new DiagnosticReportDraft.Target("HUAWEI_CLOUD", "cn-north-9", "APM_INSTANCE",
                "instance-2121291", "DPBinMedService");
        var timeline = List.of(new DiagnosticReportDraft.TimelineItem("TIME-1", start, "ALERT", "Alarm active"));
        var observations = List.of(new Observation("OBS-1", "INV-1", "EVID-1", "EDEN_HIGH", start));
        var hypotheses = List.of(new Hypothesis("HYP-1", "INV-1", "TEMPLATE_METRIC_MISMATCH",
                HypothesisStatus.SUPPORTED, List.of("EVID-1"), start.plusSeconds(30)));
        var conclusions = List.of(new Conclusion("CON-1", "INV-1", ConclusionType.ROOT_CAUSE_IDENTIFIED,
                "EDEN_CODECACHE_MISMATCH", List.of("EVID-1"), start.plusSeconds(60)));
        var evidence = List.of(new EvidenceRecord("EVID-1", "APM_TREND", "artifact-1", "EDEN_MATCH",
                "c".repeat(64), 128, start.plusSeconds(10)));
        return new DiagnosisReportSourceSnapshot(incident, investigation, run, target, start.plusSeconds(300),
                timeline, observations, hypotheses, conclusions, evidence, List.of(), List.of(), "1.0.0",
                sourceDigest, start.plusSeconds(400), Map.of());
    }

    private static final class MemoryRepository implements DiagnosticReportRepository {
        private final Map<String, PublishedDiagnosticReport> reports = new LinkedHashMap<>();
        private final List<ReportAudit> audits = new ArrayList<>();
        @Override public void publish(PublishedDiagnosticReport report, ReportAudit audit) {
            reports.put(report.reportId(), report);
            audits.add(audit);
        }
        @Override public Optional<PublishedDiagnosticReport> find(String reportId) {
            return Optional.ofNullable(reports.get(reportId));
        }
        @Override public Optional<PublishedDiagnosticReport> findByRequest(String requestId) {
            return reports.values().stream().filter(report -> requestId.equals(report.requestId())).findFirst();
        }
        @Override public Optional<PublishedDiagnosticReport> latest(String investigationId) {
            return reports.values().stream().filter(report -> investigationId.equals(report.investigationId()))
                    .max(java.util.Comparator.comparingLong(PublishedDiagnosticReport::revisionNumber));
        }
        @Override public List<PublishedDiagnosticReport> page(String investigationId, Long beforeRevision, int limit) {
            return reports.values().stream().filter(report -> investigationId.equals(report.investigationId()))
                    .filter(report -> beforeRevision == null || report.revisionNumber() < beforeRevision)
                    .sorted(java.util.Comparator.comparingLong(PublishedDiagnosticReport::revisionNumber).reversed())
                    .limit(limit).toList();
        }
    }
}
