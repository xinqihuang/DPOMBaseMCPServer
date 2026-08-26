/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import com.huawei.smartom.agentic.diagnosis.model.Investigation;

import java.time.Instant;

/**
 * 不包含预算细节、证据或模型内容的权威调查快照。
 *
 * @param investigationId 调查身份
 * @param incidentId 事件身份
 * @param status 状态
 * @param aggregateVersion 聚合版本
 * @param activeRunId 当前运行身份
 * @param authorityEpoch 权威纪元
 * @param updatedAt 更新时间
 * @author Codex
 * @since 2026-08-25
 */
public record InvestigationSnapshotResponse(String investigationId, String incidentId, String status,
                                            long aggregateVersion, String activeRunId,
                                            String authorityEpoch, Instant updatedAt) {
    /**
     * 从权威聚合建立安全投影。
     *
     * @param value 权威聚合
     * @return 安全快照
     */
    public static InvestigationSnapshotResponse from(Investigation value) {
        return new InvestigationSnapshotResponse(value.investigationId(), value.incidentId(),
                value.status().name(), value.version(), value.activeRunId(),
                value.authorityEpoch().epoch(), value.updatedAt());
    }
}
