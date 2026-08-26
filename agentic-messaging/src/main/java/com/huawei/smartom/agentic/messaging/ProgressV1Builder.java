/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;

/**
 * Builds immutable RFC 8785 Diagnosis Progress v1 records from persisted progress.
 * @author Codex
 * @since 2026-08-25
 */
public final class ProgressV1Builder {
    public static final String TOPIC = "dpom.diagnosis-progress.v1";
    private final ObjectMapper mapper;

    /**
     * Creates a builder.
     * @param mapper JSON mapper
     */
    public ProgressV1Builder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Builds canonical progress content with its independent sequence.
     * @param progress persisted progress
     * @param authority source authority
     * @param percentComplete optional percentage
     * @param checkpointRef optional checkpoint
     * @return frozen progress publication
     * @throws IllegalArgumentException when facts violate the contract
     */
    public FrozenPublication build(ProgressRecord progress, AuthorityEpoch authority,
                                   Integer percentComplete, String checkpointRef) {
        if (progress == null || authority == null
                || !"DPOMBaseMCPServer".equals(authority.service())) {
            throw new IllegalArgumentException("progress source authority");
        }
        if (percentComplete != null && (percentComplete < 0 || percentComplete > 100)) {
            throw new IllegalArgumentException("percentComplete");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("progressId", progress.progressId());
        root.put("schemaVersion", "1.0");
        root.put("occurredAt", progress.occurredAt().toString());
        root.put("investigationId", progress.investigationId());
        root.put("runId", progress.runId());
        root.put("progressSequence", progress.progressSequence());
        root.put("aggregateVersion", progress.aggregateVersion());
        ObjectNode authorityNode = mapper.createObjectNode();
        authorityNode.put("service", authority.service());
        authorityNode.put("authorityEpoch", authority.epoch());
        root.set("sourceAuthority", authorityNode);
        root.put("status", progress.status().name());
        root.put("stage", progress.stageCode());
        root.put("summaryCode", progress.summaryCode());
        if (percentComplete != null) {
            root.put("percentComplete", percentComplete);
        }
        if (checkpointRef != null) {
            root.put("checkpointRef", checkpointRef);
        }
        CanonicalRecord canonical = CanonicalJson.encode(mapper, root);
        return new FrozenPublication("progress-" + progress.progressId(), progress.progressId(),
                progress.investigationId(), TOPIC, progress.progressSequence(),
                progress.aggregateVersion(), authority.epoch(), canonical.bytes(), canonical.sha256(),
                progress.occurredAt());
    }
}
