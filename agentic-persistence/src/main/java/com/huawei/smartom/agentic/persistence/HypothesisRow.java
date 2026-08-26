/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.Hypothesis;

import java.time.Instant;

/**
 * Hypothesis 的有界持久化行。
 *
 * @param hypothesisId 假设身份
 * @param investigationId 调查身份
 * @param statementCode 陈述码
 * @param status 假设状态
 * @param evidenceRefs 换行分隔的安全引用
 * @param updatedAt 更新时间
 * @author Codex
 * @since 2026-08-25
 */
public record HypothesisRow(String hypothesisId, String investigationId, String statementCode,
                            String status, String evidenceRefs, Instant updatedAt) {

    /**
     * 从领域假设创建持久化行。
     *
     * @param value 领域假设
     * @return 持久化行
     */
    public static HypothesisRow from(Hypothesis value) {
        return new HypothesisRow(value.hypothesisId(), value.investigationId(), value.statementCode(),
                value.status().name(), String.join("\n", value.evidenceRefs()), value.updatedAt());
    }
}
