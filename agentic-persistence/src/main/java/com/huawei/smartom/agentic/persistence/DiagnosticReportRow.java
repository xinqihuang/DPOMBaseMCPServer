/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import java.time.Instant;

/**
 * Phase 5 diagnosis-only 报告数据库行。
 *
 * @param id 数据库身份
 * @param reportId 报告身份
 * @param requestId 请求身份
 * @param requestDigest 请求摘要
 * @param incidentId 事件身份
 * @param investigationId 调查身份
 * @param runId 运行身份
 * @param revisionNumber 修订号
 * @param supersedesReportId 被替代报告
 * @param changeReason 变更原因
 * @param sourceDigest 来源摘要
 * @param canonicalJson 规范 JSON
 * @param reportDigest 报告摘要
 * @param completeness 完整性
 * @param createdBy 创建者
 * @param createdAt 创建时间
 * @author Codex
 * @since 2026-08-26
 */
public record DiagnosticReportRow(Long id, String reportId, String requestId, String requestDigest,
        String incidentId, String investigationId, String runId, long revisionNumber,
        String supersedesReportId, String changeReason, String sourceDigest, String canonicalJson,
        String reportDigest, String completeness, String createdBy, Instant createdAt) { }
