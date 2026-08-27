/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

import java.time.Instant;

/**
 * 将有界 adapter 响应写入受控证据 Artifact 的外向端口。
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface BoundedEvidenceArtifactStore {
    /**
     * 写入已由现有 monitoring service 限界的响应。
     *
     * @param collectionId 调用方提供的证据集合编号
     * @param evidenceType 证据类型
     * @param boundedValue 有界响应，仅在 adapter 边界内可见
     * @param capturedAt 采集时间
     * @return 中立引用、摘要和大小
     */
    StoredEvidence store(String collectionId, String evidenceType, Object boundedValue, Instant capturedAt);
}
