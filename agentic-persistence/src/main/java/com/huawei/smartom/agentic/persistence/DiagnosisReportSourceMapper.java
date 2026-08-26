/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.InvestigationRun;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/**
 * Phase 5 报告源只读查询映射。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Mapper
public interface DiagnosisReportSourceMapper {
    /**
     * @param investigationId 调查身份
     * @return 最新运行
     */
    Optional<InvestigationRun> latestRun(String investigationId);

    /**
     * @param investigationId 调查身份
     * @return 观察事实
     */
    List<Observation> observations(String investigationId);

    /**
     * @param investigationId 调查身份
     * @return 假设
     */
    List<HypothesisRow> hypotheses(String investigationId);

    /**
     * @param investigationId 调查身份
     * @return 结论
     */
    Optional<ConclusionRow> conclusion(String investigationId);

    /**
     * @param investigationId 调查身份
     * @return 最新发布源
     */
    Optional<PublicationSourceRow> latestPublication(String investigationId);

    /**
     * 已冻结 publication intent 的最小来源行。
     *
     * @param eventId 事件身份
     * @param runId 运行身份
     * @param canonicalContent 规范内容
     * @param canonicalSha256 内容摘要
     * @param createdAt 创建时间
     * @author Codex
     * @since 2026-08-26
     */
    record PublicationSourceRow(String eventId, String runId, byte[] canonicalContent,
                                String canonicalSha256, Instant createdAt) { }
}
