/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.Conclusion;

import java.time.Instant;

/**
 * Conclusion 的有界持久化行。
 *
 * @param conclusionId 结论身份
 * @param investigationId 调查身份
 * @param conclusionType 结论类型
 * @param summaryCode 摘要码
 * @param evidenceRefs 换行分隔的安全引用
 * @param concludedAt 结论时间
 * @author Codex
 * @since 2026-08-25
 */
public record ConclusionRow(String conclusionId, String investigationId, String conclusionType,
                            String summaryCode, String evidenceRefs, Instant concludedAt) {

    /**
     * 从领域结论创建持久化行。
     *
     * @param value 领域结论
     * @return 持久化行
     */
    public static ConclusionRow from(Conclusion value) {
        return new ConclusionRow(value.conclusionId(), value.investigationId(), value.type().name(),
                value.summaryCode(), String.join("\n", value.evidenceRefs()), value.concludedAt());
    }
}
