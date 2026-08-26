/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 框架无关的 diagnosis-only 权威报告草稿，正文只含有界事实和引用。
 *
 * @param reportProfile 报告档案
 * @param identity 身份
 * @param target 目标
 * @param incidentWindow 事件窗口
 * @param timeline 时间线
 * @param observations 观察事实
 * @param hypotheses 假设
 * @param conclusions 结论
 * @param evidenceReferences 证据引用
 * @param gapCodes 缺口代码
 * @param recommendations 建议
 * @param evaluationOutcome 评价轴
 * @param provenance 来源组件
 * @param generatedAt 生成时间
 * @param completeness 完整性
 * @param extensions 扩展
 * @author Codex
 * @since 2026-08-26
 */
public record DiagnosticReportDraft(
        String reportProfile,
        Identity identity,
        Target target,
        Window incidentWindow,
        List<TimelineItem> timeline,
        List<Claim> observations,
        List<Claim> hypotheses,
        List<Claim> conclusions,
        List<EvidenceReference> evidenceReferences,
        List<String> gapCodes,
        List<Recommendation> recommendations,
        String evaluationOutcome,
        List<Component> provenance,
        Instant generatedAt,
        String completeness,
        Map<String, Map<String, String>> extensions) {
    public DiagnosticReportDraft {
        timeline = List.copyOf(timeline);
        observations = List.copyOf(observations);
        hypotheses = List.copyOf(hypotheses);
        conclusions = List.copyOf(conclusions);
        evidenceReferences = List.copyOf(evidenceReferences);
        gapCodes = List.copyOf(gapCodes);
        recommendations = List.copyOf(recommendations);
        provenance = List.copyOf(provenance);
        extensions = Map.copyOf(extensions);
    }

    /**
     * 报告源身份。
     *
     * @param incidentId 事件身份
     * @param investigationId 调查身份
     * @param runId 运行身份
     * @author Codex
     * @since 2026-08-26
     */
    public record Identity(String incidentId, String investigationId, String runId) { }

    /**
     * 受影响对象身份。
     *
     * @param provider 提供方
     * @param region 区域
     * @param resourceType 资源类型
     * @param resourceId 资源身份
     * @param displayName 展示名称
     * @author Codex
     * @since 2026-08-26
     */
    public record Target(String provider, String region, String resourceType, String resourceId, String displayName) { }

    /**
     * 时间窗口。
     *
     * @param from 开始时间
     * @param to 结束时间
     * @author Codex
     * @since 2026-08-26
     */
    public record Window(Instant from, Instant to) { }

    /**
     * 时间线项目。
     *
     * @param itemId 项目身份
     * @param at 发生时间
     * @param stateCode 状态码
     * @param summary 摘要
     * @author Codex
     * @since 2026-08-26
     */
    public record TimelineItem(String itemId, Instant at, String stateCode, String summary) { }

    /**
     * 明确区分类型和置信度的声明。
     *
     * @param itemId 项目身份
     * @param claimType 声明类型
     * @param summary 摘要
     * @param confidenceBasisPoints 置信度基点
     * @param supportingEvidenceRefs 支持证据
     * @param contradictingEvidenceRefs 反向证据
     * @param disposition 诊断结论轴
     * @author Codex
     * @since 2026-08-26
     */
    public record Claim(String itemId, String claimType, String summary, int confidenceBasisPoints,
                        List<String> supportingEvidenceRefs, List<String> contradictingEvidenceRefs,
                        String disposition) {
        public Claim {
            supportingEvidenceRefs = List.copyOf(supportingEvidenceRefs);
            contradictingEvidenceRefs = List.copyOf(contradictingEvidenceRefs);
        }
    }

    /**
     * 不携带证据正文的不可变引用。
     *
     * @param evidenceId 证据身份
     * @param sourceCapability 来源能力
     * @param sourceAdapter 来源适配器
     * @param artifactRef 制品引用
     * @param sha256 摘要
     * @param collectedAt 收集时间
     * @param window 收集窗口
     * @param targetResourceId 目标资源身份
     * @param sensitivity 敏感级别
     * @param redaction 脱敏标记
     * @author Codex
     * @since 2026-08-26
     */
    public record EvidenceReference(String evidenceId, String sourceCapability, String sourceAdapter,
                                    String artifactRef, String sha256, Instant collectedAt, Window window,
                                    String targetResourceId, String sensitivity, String redaction) { }

    /**
     * 仅供人工评估的建议。
     *
     * @param itemId 项目身份
     * @param summary 摘要
     * @param supportingEvidenceRefs 支持证据
     * @param safetyCode 安全码
     * @param executionState 执行状态
     * @author Codex
     * @since 2026-08-26
     */
    public record Recommendation(String itemId, String summary, List<String> supportingEvidenceRefs,
                                 String safetyCode, String executionState) {
        public Recommendation {
            supportingEvidenceRefs = List.copyOf(supportingEvidenceRefs);
        }
    }

    /**
     * 生成组件来源。
     *
     * @param componentId 组件身份
     * @param componentVersion 组件版本
     * @param contractVersion 契约版本
     * @param sourceDigest 来源摘要
     * @author Codex
     * @since 2026-08-26
     */
    public record Component(String componentId, String componentVersion, String contractVersion,
                            String sourceDigest) { }
}
