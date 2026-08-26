/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.port.DiagnosticReportRepository;
import com.huawei.smartom.agentic.diagnosis.report.PublishedDiagnosticReport;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * diagnosis-only 报告的 MyBatis 事务适配器。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Repository
@ConditionalOnProperty(prefix = "dpom.investigation.persistence", name = "enabled", havingValue = "true")
public class MyBatisDiagnosticReportRepository implements DiagnosticReportRepository {
    private final DiagnosticReportMapper mapper;
    /**
     * 创建适配器。
     *
     * @param mapper SQL 映射
     */
    public MyBatisDiagnosticReportRepository(DiagnosticReportMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void publish(PublishedDiagnosticReport report, ReportAudit audit) {
        mapper.insertReport(toRow(report));
        mapper.insertAudit(new DiagnosticReportAuditRow(audit.auditId(), audit.reportId(), audit.action(),
                audit.actorId(), audit.reasonCode(), audit.recordedAt()));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublishedDiagnosticReport> find(String reportId) {
        return mapper.find(reportId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublishedDiagnosticReport> findByRequest(String requestId) {
        return mapper.findByRequest(requestId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublishedDiagnosticReport> latest(String investigationId) {
        return mapper.latest(investigationId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<PublishedDiagnosticReport> page(String investigationId, Long beforeRevision, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("REPORT_PAGE_LIMIT_INVALID");
        }
        return mapper.page(investigationId, beforeRevision, limit).stream().map(this::toDomain).toList();
    }

    private DiagnosticReportRow toRow(PublishedDiagnosticReport value) {
        return new DiagnosticReportRow(null, value.reportId(), value.requestId(), value.requestDigest(),
                value.incidentId(), value.investigationId(), value.runId(), value.revisionNumber(),
                value.supersedesReportId(), value.changeReason(), value.sourceDigest(), value.canonicalJson(),
                value.reportDigest(), value.completeness(), value.createdBy(), value.createdAt());
    }

    private PublishedDiagnosticReport toDomain(DiagnosticReportRow value) {
        return new PublishedDiagnosticReport(value.reportId(), value.requestId(), value.requestDigest(),
                value.incidentId(), value.investigationId(), value.runId(), value.revisionNumber(),
                value.supersedesReportId(), value.changeReason(), value.sourceDigest(), value.canonicalJson(),
                value.reportDigest(), value.completeness(), value.createdBy(), value.createdAt());
    }
}
