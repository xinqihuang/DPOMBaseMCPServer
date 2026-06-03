/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import java.util.List;

/**
 * {@code list_logs} 工具的响应 DTO，对应华为云 LTS {@code ListLogs} 接口的响应。
 *
 * <p>当请求里 {@code isAnalysisQuery=true} 时，结果通过 {@link #analysisLogs} 返回而非
 * {@link #logs}；上游为兼容多种聚合形态把该字段类型保留为通用 {@code Object}。
 *
 * @param count            命中总条数
 * @param logs             日志条目列表，可能为空但不会为 {@code null}
 * @param isQueryComplete  上游查询是否已完成（{@code false} 表示结果可能被截断，需要分页继续）
 * @param analysisLogs     SQL 分析结果，仅当 {@code isAnalysisQuery=true} 时非空，每条元素结构由 SQL 决定
 * @author h00884391
 * @since 2026-06-03
 */
public record LtsListLogsResponse(
        Integer count,
        List<LtsLogEntry> logs,
        Boolean isQueryComplete,
        List<Object> analysisLogs) {
}
