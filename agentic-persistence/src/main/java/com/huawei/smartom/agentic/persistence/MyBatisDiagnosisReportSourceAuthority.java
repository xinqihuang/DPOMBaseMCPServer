/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.Hypothesis;
import com.huawei.smartom.agentic.diagnosis.model.HypothesisStatus;
import com.huawei.smartom.agentic.diagnosis.model.Incident;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import com.huawei.smartom.agentic.diagnosis.port.DiagnosisReportSourceAuthority;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRecord;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.report.DiagnosisReportSourceSnapshot;
import com.huawei.smartom.agentic.diagnosis.report.DiagnosticReportDraft;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 从 Investigation 表和冻结 publication intent 重建报告源。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Component
@ConditionalOnProperty(prefix = "dpom.investigation.persistence", name = "enabled", havingValue = "true")
public class MyBatisDiagnosisReportSourceAuthority implements DiagnosisReportSourceAuthority {
    private final InvestigationRepository investigations;
    private final DiagnosisReportSourceMapper mapper;
    private final ObjectMapper json;

    /**
     * 创建只读来源适配器。
     *
     * @param investigations 调查仓储
     * @param mapper 来源查询映射
     * @param json JSON 解析器
     */
    public MyBatisDiagnosisReportSourceAuthority(InvestigationRepository investigations,
            DiagnosisReportSourceMapper mapper, ObjectMapper json) {
        this.investigations = investigations;
        this.mapper = mapper;
        this.json = json;
    }

    /** {@inheritDoc} */
    @Override
    public DiagnosisReportSourceSnapshot freeze(String investigationId) {
        var investigation = investigations.find(investigationId).orElseThrow();
        var run = mapper.latestRun(investigationId).orElseThrow();
        var publication = mapper.latestPublication(investigationId).orElseThrow();
        verify(publication.canonicalContent(), publication.canonicalSha256());
        JsonNode event = parse(publication.canonicalContent());
        if (!event.path("schemaVersion").asText().startsWith("2.")) {
            throw new IllegalStateException("REPORT_SOURCE_VERSION_UNSUPPORTED");
        }
        Instant occurredAt = Instant.parse(event.path("occurredAt").asText());
        Instant terminalAt = publication.createdAt().isBefore(occurredAt) ? occurredAt : publication.createdAt();
        Incident incident = new Incident(investigation.incidentId(), "DIAGNOSIS_EVENT", occurredAt);
        List<Observation> observations = mapper.observations(investigationId);
        List<Hypothesis> hypotheses = mapper.hypotheses(investigationId).stream().map(this::hypothesis).toList();
        List<Conclusion> conclusions = mapper.conclusion(investigationId).stream().map(this::conclusion).toList();
        List<EvidenceRecord> evidence = evidence(event, observations, hypotheses, conclusions, publication.createdAt());
        var target = new DiagnosticReportDraft.Target("HUAWEI_CLOUD", "unknown-region", "INCIDENT_SCOPE",
                investigation.incidentId(), investigation.incidentId());
        var timeline = List.of(new DiagnosticReportDraft.TimelineItem("TIME-" + publication.eventId(),
                occurredAt, state(event), "Persisted terminal diagnosis event"));
        return new DiagnosisReportSourceSnapshot(incident, investigation, run, target, terminalAt,
                timeline, observations, hypotheses, conclusions, evidence, List.of("MISSING_PROVENANCE"), List.of(),
                "1.0.0", publication.canonicalSha256(), terminalAt,
                Map.of("provider.huawei.diagnosis-event@1", Map.of("eventId", publication.eventId())));
    }

    private List<EvidenceRecord> evidence(JsonNode event, List<Observation> observations,
            List<Hypothesis> hypotheses, List<Conclusion> conclusions, Instant capturedAt) {
        JsonNode manifest = event.path("evidenceManifest");
        String digest = manifest.path("sha256").asText();
        long size = manifest.path("byteSize").asLong();
        String manifestId = manifest.path("manifestId").asText();
        Set<String> refs = new LinkedHashSet<>();
        observations.forEach(value -> refs.add(value.evidenceRef()));
        hypotheses.forEach(value -> refs.addAll(value.evidenceRefs()));
        conclusions.forEach(value -> refs.addAll(value.evidenceRefs()));
        List<EvidenceRecord> result = new ArrayList<>();
        refs.stream().sorted().forEach(ref -> result.add(new EvidenceRecord(ref, "EVIDENCE_MANIFEST",
                manifestId + "/" + ref, summary(ref, observations), digest, size, capturedAt)));
        return result;
    }

    private String summary(String ref, List<Observation> observations) {
        return observations.stream().filter(value -> ref.equals(value.evidenceRef()))
                .map(Observation::summaryCode).findFirst().orElse("REFERENCED_EVIDENCE");
    }

    private Hypothesis hypothesis(HypothesisRow row) {
        return new Hypothesis(row.hypothesisId(), row.investigationId(), row.statementCode(),
                HypothesisStatus.valueOf(row.status()), refs(row.evidenceRefs()), row.updatedAt());
    }

    private Conclusion conclusion(ConclusionRow row) {
        return new Conclusion(row.conclusionId(), row.investigationId(), ConclusionType.valueOf(row.conclusionType()),
                row.summaryCode(), refs(row.evidenceRefs()), row.concludedAt());
    }

    private List<String> refs(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\n"));
    }

    private String state(JsonNode event) {
        return event.path("eventType").asText().endsWith("completed") ? "COMPLETED" : "INCONCLUSIVE";
    }

    private JsonNode parse(byte[] content) {
        try {
            return json.readTree(content);
        }
        catch (IOException error) {
            throw new IllegalStateException("REPORT_SOURCE_JSON_INVALID");
        }
    }

    private void verify(byte[] content, String expected) {
        try {
            String actual = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            if (!actual.equals(expected)) {
                throw new IllegalStateException("REPORT_SOURCE_DIGEST_MISMATCH");
            }
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("REPORT_SHA256_UNAVAILABLE");
        }
    }
}
