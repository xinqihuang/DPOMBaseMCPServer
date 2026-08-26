/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.report;

import java.time.Instant;

/**
 * 已发布 diagnosis-only 权威报告的不可变存储值。
 *
 * @param reportId 报告身份
 * @param requestId 幂等请求身份
 * @param requestDigest 请求摘要
 * @param incidentId 事件身份
 * @param investigationId 调查身份
 * @param runId 运行身份
 * @param revisionNumber 修订号
 * @param supersedesReportId 被替代报告
 * @param changeReason 修改原因
 * @param sourceDigest 冻结源摘要
 * @param canonicalJson 权威 JSON
 * @param reportDigest 报告摘要
 * @param completeness 完整性
 * @param createdBy 创建人
 * @param createdAt 创建时间
 * @author Codex
 * @since 2026-08-26
 */
public record PublishedDiagnosticReport(
        String reportId,
        String requestId,
        String requestDigest,
        String incidentId,
        String investigationId,
        String runId,
        long revisionNumber,
        String supersedesReportId,
        String changeReason,
        String sourceDigest,
        String canonicalJson,
        String reportDigest,
        String completeness,
        String createdBy,
        Instant createdAt) { }
