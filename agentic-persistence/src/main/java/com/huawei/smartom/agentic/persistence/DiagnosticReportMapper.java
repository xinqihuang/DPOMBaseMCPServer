/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Phase 5 diagnosis-only 报告 SQL 映射。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Mapper
public interface DiagnosticReportMapper {
    /**
     * @param row 报告行
     * @return 插入数量
     */
    int insertReport(DiagnosticReportRow row);

    /**
     * @param row 审计行
     * @return 插入数量
     */
    int insertAudit(DiagnosticReportAuditRow row);

    /**
     * @param reportId 报告身份
     * @return 报告行
     */
    Optional<DiagnosticReportRow> find(String reportId);

    /**
     * @param requestId 请求身份
     * @return 报告行
     */
    Optional<DiagnosticReportRow> findByRequest(String requestId);

    /**
     * @param investigationId 调查身份
     * @return 最新报告行
     */
    Optional<DiagnosticReportRow> latest(String investigationId);

    /**
     * @param investigationId 调查身份
     * @param beforeRevision 修订游标
     * @param limit 数量上限
     * @return 报告页
     */
    List<DiagnosticReportRow> page(@Param("investigationId") String investigationId,
                                   @Param("beforeRevision") Long beforeRevision, @Param("limit") int limit);
}
