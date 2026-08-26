/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.report;

import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.Hypothesis;
import com.huawei.smartom.agentic.diagnosis.model.Incident;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationRun;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 从持久化 Investigation 权威事实冻结的报告输入。
 *
 * @param incident 事件
 * @param investigation 调查
 * @param run 运行
 * @param target 对象
 * @param incidentTo 事件窗口结束
 * @param timeline 时间线
 * @param observations 观察
 * @param hypotheses 假设
 * @param conclusions 结论
 * @param evidence 证据引用
 * @param gapCodes 缺口代码
 * @param recommendations 建议
 * @param componentVersion 组件版本
 * @param sourceDigest 冻结输入摘要
 * @param generatedAt 生成时刻
 * @param extensions 供应商扩展
 * @author Codex
 * @since 2026-08-26
 */
public record DiagnosisReportSourceSnapshot(
        Incident incident,
        Investigation investigation,
        InvestigationRun run,
        DiagnosticReportDraft.Target target,
        Instant incidentTo,
        List<DiagnosticReportDraft.TimelineItem> timeline,
        List<Observation> observations,
        List<Hypothesis> hypotheses,
        List<Conclusion> conclusions,
        List<EvidenceRecord> evidence,
        List<String> gapCodes,
        List<DiagnosticReportDraft.Recommendation> recommendations,
        String componentVersion,
        String sourceDigest,
        Instant generatedAt,
        Map<String, Map<String, String>> extensions) {
    public DiagnosisReportSourceSnapshot {
        timeline = List.copyOf(timeline);
        observations = List.copyOf(observations);
        hypotheses = List.copyOf(hypotheses);
        conclusions = List.copyOf(conclusions);
        evidence = List.copyOf(evidence);
        gapCodes = List.copyOf(gapCodes);
        recommendations = List.copyOf(recommendations);
        extensions = Map.copyOf(extensions);
    }
}
