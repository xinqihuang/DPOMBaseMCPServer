/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.smartom.agentic.diagnosis.port.DiagnosisReportSourceAuthority;
import com.huawei.smartom.agentic.diagnosis.port.DiagnosticReportRepository;
import com.huawei.smartom.agentic.diagnosis.port.DiagnosticReportRepository.ReportAudit;
import com.huawei.smartom.agentic.diagnosis.report.DiagnosisOnlyReportBuilder;
import com.huawei.smartom.agentic.diagnosis.report.DiagnosticReportDraft;
import com.huawei.smartom.agentic.diagnosis.report.PublishedDiagnosticReport;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 从服务本地持久化事实生成、发布和重放 diagnosis-only 报告。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Service
@ConditionalOnBean({DiagnosisReportSourceAuthority.class, DiagnosticReportRepository.class})
public class DiagnosisReportApplicationService {
    private static final Pattern PROHIBITED = Pattern.compile(
            "(?i)(authorization\\s*[:=]|bearer\\s+|api[_-]?key|password|secret[_-]?key|raw model|prompt body)");
    private final DiagnosisReportSourceAuthority authority;
    private final DiagnosticReportRepository repository;
    private final DiagnosticReportApiProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final DiagnosisOnlyReportBuilder builder = new DiagnosisOnlyReportBuilder();

    /**
     * 创建报告应用服务。
     *
     * @param authority 来源权威
     * @param repository 报告仓储
     * @param properties 配置
     * @param json JSON 映射器
     * @param clock 时钟
     */
    public DiagnosisReportApplicationService(DiagnosisReportSourceAuthority authority,
            DiagnosticReportRepository repository, DiagnosticReportApiProperties properties,
            ObjectMapper json, Clock clock) {
        this.authority = authority;
        this.repository = repository;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    /**
     * 生成并事务发布报告；相同请求和摘要返回现有结果。
     *
     * @param command 生成命令
     * @return 已发布报告
     */
    public PublishedDiagnosticReport generate(GenerateCommand command) {
        enabled(properties.generationEnabled(), "REPORT_GENERATION_DISABLED");
        var source = authority.freeze(command.investigationId());
        DiagnosticReportDraft draft = builder.build(source);
        String requestDigest = hash(command.requestId() + '\n' + source.sourceDigest() + '\n'
                + command.expectedLatestRevision() + '\n' + String.valueOf(command.changeReason()));
        Optional<PublishedDiagnosticReport> existing = repository.findByRequest(command.requestId());
        if (existing.isPresent()) {
            require(existing.get().requestDigest().equals(requestDigest), "REPORT_REQUEST_DIGEST_CONFLICT");
            return existing.get();
        }
        long latest = repository.latest(command.investigationId())
                .map(PublishedDiagnosticReport::revisionNumber).orElse(0L);
        require(latest == command.expectedLatestRevision(), "REPORT_REVISION_STALE");
        var parent = repository.latest(command.investigationId());
        require(latest == 0 || command.changeReason() != null, "REPORT_CHANGE_REASON_REQUIRED");
        String reportId = "REPORT-DIAG-" + hash(source.sourceDigest() + '\n' + (latest + 1)).substring(0, 24);
        JsonNode report = document(reportId, latest + 1, parent.map(PublishedDiagnosticReport::reportId).orElse(null),
                command.changeReason(), draft);
        String canonicalJson = canonical(report);
        PublishedDiagnosticReport published = new PublishedDiagnosticReport(reportId, command.requestId(),
                requestDigest, draft.identity().incidentId(), draft.identity().investigationId(),
                draft.identity().runId(),
                latest + 1, parent.map(PublishedDiagnosticReport::reportId).orElse(null), command.changeReason(),
                source.sourceDigest(), canonicalJson, report.path("reportDigest").asText(), draft.completeness(),
                command.actorId(), clock.instant());
        repository.publish(published, new ReportAudit("AUDIT-" + hash(requestDigest).substring(0, 24), reportId,
                "PUBLISHED", command.actorId(), "GENERATED", clock.instant()));
        return published;
    }

    /**
     * 精确读取并验证持久化摘要。
     *
     * @param reportId 报告身份
     * @return 报告 JSON
     */
    public JsonNode exact(String reportId) {
        enabled(properties.readApiEnabled(), "REPORT_READ_DISABLED");
        JsonNode report = parse(repository.find(reportId).orElseThrow().canonicalJson());
        verifyDigest(report);
        return report;
    }

    /**
     * 只从已持久化权威 JSON 确定性重放。
     *
     * @param reportId 报告身份
     * @return 报告 JSON
     */
    public JsonNode replay(String reportId) {
        return exact(reportId);
    }

    /**
     * 有界读取调查报告历史。
     *
     * @param investigationId 调查身份
     * @param beforeRevision 修订游标
     * @param limit 数量上限
     * @return 报告页
     */
    public List<PublishedDiagnosticReport> page(String investigationId, Long beforeRevision, int limit) {
        enabled(properties.readApiEnabled(), "REPORT_READ_DISABLED");
        return repository.page(investigationId, beforeRevision, limit);
    }

    private JsonNode document(String reportId, long revision, String parent, String reason,
                              DiagnosticReportDraft draft) {
        ObjectNode report = json.createObjectNode();
        report.put("contractType", "diagnostic-report");
        report.put("schemaVersion", "1.0.0");
        report.put("reportProfile", draft.reportProfile());
        report.put("reportId", reportId);
        report.put("revision", revision);
        if (parent == null) {
            report.putNull("supersedesReportId");
        }
        else {
            report.put("supersedesReportId", parent);
        }
        ArrayNode reasons = report.putArray("changeReasons");
        if (reason != null) {
            reasons.add(reason);
        }
        report.set("identity", json.valueToTree(draft.identity()));
        report.set("target", json.valueToTree(draft.target()));
        report.set("incidentWindow", json.valueToTree(draft.incidentWindow()));
        report.set("timeline", json.valueToTree(draft.timeline()));
        report.set("observations", claims(draft.observations()));
        report.set("hypotheses", claims(draft.hypotheses()));
        report.set("conclusions", claims(draft.conclusions()));
        report.set("evidenceReferences", json.valueToTree(draft.evidenceReferences()));
        report.set("gapCodes", json.valueToTree(draft.gapCodes()));
        report.set("recommendations", json.valueToTree(draft.recommendations()));
        ObjectNode evaluation = report.putObject("evaluation");
        evaluation.put("outcome", draft.evaluationOutcome());
        evaluation.putObject("lineage");
        evaluation.putArray("judges");
        report.set("provenance", json.valueToTree(draft.provenance()));
        report.put("generatedAt", draft.generatedAt().toString());
        report.put("completeness", draft.completeness());
        report.set("extensions", json.valueToTree(draft.extensions()));
        rejectProhibited(report);
        report.put("reportDigest", digest(report));
        return report;
    }

    private ArrayNode claims(List<DiagnosticReportDraft.Claim> claims) {
        ArrayNode values = json.valueToTree(claims);
        values.forEach(value -> {
            if (value.path("disposition").isNull()) {
                ((ObjectNode) value).remove("disposition");
            }
        });
        return values;
    }

    private String digest(JsonNode report) {
        ObjectNode copy = report.deepCopy();
        copy.remove("reportDigest");
        return hashBytes(canonical(copy).getBytes(StandardCharsets.UTF_8));
    }

    private void verifyDigest(JsonNode report) {
        require(digest(report).equals(report.path("reportDigest").asText()), "REPORT_DIGEST_MISMATCH");
    }

    private String canonical(JsonNode report) {
        try {
            return new String(new JsonCanonicalizer(json.writeValueAsBytes(report)).getEncodedUTF8(),
                    StandardCharsets.UTF_8);
        }
        catch (IOException error) {
            throw new IllegalStateException("REPORT_SERIALIZATION_FAILED");
        }
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        }
        catch (JsonProcessingException error) {
            throw new IllegalStateException("REPORT_JSON_INVALID");
        }
    }

    private void rejectProhibited(JsonNode node) {
        if (node.isTextual()) {
            require(!PROHIBITED.matcher(node.asText()).find(), "REPORT_PROHIBITED_CONTENT");
        }
        else if (node.isContainerNode()) {
            node.forEach(this::rejectProhibited);
        }
    }

    private String hash(String value) {
        return hashBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String hashBytes(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("REPORT_SHA256_UNAVAILABLE");
        }
    }

    private void enabled(boolean value, String reason) {
        require(value, reason);
    }

    private void require(boolean value, String reason) {
        if (!value) {
            throw new IllegalStateException(reason);
        }
    }

    /**
     * 报告生成命令。
     *
     * @param requestId 请求身份
     * @param investigationId 调查身份
     * @param expectedLatestRevision 预期最新修订
     * @param changeReason 变更原因
     * @param actorId 操作者身份
     * @author Codex
     * @since 2026-08-26
     */
    public record GenerateCommand(String requestId, String investigationId, long expectedLatestRevision,
                                  String changeReason, String actorId) { }
}
