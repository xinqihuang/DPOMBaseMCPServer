/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;

import java.time.Instant;

/**
 * 仅含稳定状态编码的安全进度投影。
 *
 * @param sequence 单调查单调序号
 * @param aggregateVersion 聚合版本
 * @param status 状态
 * @param stageCode 阶段编码
 * @param summaryCode 摘要编码
 * @param occurredAt 发生时间
 * @author Codex
 * @since 2026-08-25
 */
public record ProgressResponse(long sequence, long aggregateVersion, String status,
                               String stageCode, String summaryCode, Instant occurredAt) {
    /**
     * 从持久化记录建立安全投影。
     *
     * @param value 持久化进度
     * @return 安全进度
     */
    public static ProgressResponse from(ProgressRecord value) {
        return new ProgressResponse(value.progressSequence(), value.aggregateVersion(), value.status().name(),
                value.stageCode(), value.summaryCode(), value.occurredAt());
    }
}
