/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import java.time.Instant;

/**
 * Phase 5 报告追加审计行。
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
public record DiagnosticReportAuditRow(String auditId, String reportId, String action, String actorId,
                                       String reasonCode, Instant recordedAt) { }
