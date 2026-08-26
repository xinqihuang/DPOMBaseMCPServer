/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

import com.huawei.smartom.agentic.diagnosis.port.EvidencePort;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRecord;
import com.huawei.smartom.agentic.diagnosis.port.EvidenceRequest;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateBranchResult;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentRequest;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentResponse;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将现有有界 monitoring 编排映射为 Investigation EvidencePort。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Component
@ConditionalOnBean(BoundedEvidenceArtifactStore.class)
public final class CorrelatedEvidencePortAdapter implements EvidencePort {

    private final CorrelateIncidentService service;
    private final BoundedEvidenceArtifactStore artifacts;

    /**
     * 创建关联证据端口适配器。
     *
     * @param service 现有有界关联服务
     * @param artifacts 受控 Artifact 存储
     */
    public CorrelatedEvidencePortAdapter(CorrelateIncidentService service, BoundedEvidenceArtifactStore artifacts) {
        this.service = service;
        this.artifacts = artifacts;
    }

    /**
     * 执行只读关联查询并仅返回受控 Artifact 引用。
     *
     * @param request 中立证据请求
     * @return provider-neutral 证据记录
     */
    @Override
    public List<EvidenceRecord> collect(EvidenceRequest request) {
        CorrelateIncidentRequest query = new CorrelateIncidentRequest(
                request.from().toEpochMilli(), request.to().toEpochMilli(), null,
                null, null, null, request.serviceCode());
        CorrelateIncidentResponse response = service.correlate(query);
        List<EvidenceRecord> records = new ArrayList<>();
        add(records, request, "CES_ALARMS", response.cesAlarms());
        add(records, request, "AOM_LOGS", response.aomLogs());
        add(records, request, "APM_TRACES", response.apmTraces());
        add(records, request, "APM_TOPOLOGY", response.apmTopology());
        return List.copyOf(records.subList(0, Math.min(records.size(), request.maxItems())));
    }

    private void add(List<EvidenceRecord> records, EvidenceRequest request,
                     String type, CorrelateBranchResult branch) {
        if (branch == null || branch.skipped() || branch.data() == null) {
            return;
        }
        StoredEvidence stored = artifacts.store(request.investigationId(), type, branch.data(), request.to());
        String evidenceId = request.investigationId() + ":" + type + ":" + (records.size() + 1);
        records.add(new EvidenceRecord(evidenceId, type, stored.sourceRef(), "EVIDENCE_CAPTURED",
                stored.sha256(), stored.byteSize(), request.to()));
    }
}
