/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.report.PublishedDiagnosticReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * diagnosis-only 报告的框架无关不可变仓储端口。
 *
 * @author Codex
 * @since 2026-08-26
 */
public interface DiagnosticReportRepository {
    /**
     * 原子发布报告和审计。
     *
     * @param report 报告
     * @param audit 审计
     */
    void publish(PublishedDiagnosticReport report, ReportAudit audit);

    /**
     * 按报告身份精确读取。
     *
     * @param reportId 报告身份
     * @return 报告
     */
    Optional<PublishedDiagnosticReport> find(String reportId);

    /**
     * 按幂等请求读取。
     *
     * @param requestId 请求身份
     * @return 报告
     */
    Optional<PublishedDiagnosticReport> findByRequest(String requestId);

    /**
     * 读取调查最新修订。
     *
     * @param investigationId 调查身份
     * @return 最新报告
     */
    Optional<PublishedDiagnosticReport> latest(String investigationId);

    /**
     * 有界游标读取修订历史。
     *
     * @param investigationId 调查身份
     * @param beforeRevision 修订游标
     * @param limit 数量上限
     * @return 报告页
     */
    List<PublishedDiagnosticReport> page(String investigationId, Long beforeRevision, int limit);

    /**
     * 报告生成审计值。
     *
     * @param auditId 审计身份
     * @param reportId 报告身份
     * @param action 动作
     * @param actorId 操作者身份
     * @param reasonCode 原因码
     * @param recordedAt 记录时间
     * @author Codex
     * @since 2026-08-26
     */
    record ReportAudit(String auditId, String reportId, String action, String actorId,
                       String reasonCode, Instant recordedAt) { }
}
