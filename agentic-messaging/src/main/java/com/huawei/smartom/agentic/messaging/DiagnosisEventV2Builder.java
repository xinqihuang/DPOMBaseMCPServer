/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;

/**
 * Builds immutable RFC 8785 Diagnosis Event v2 records from persisted terminal facts.
 * @author Codex
 * @since 2026-08-25
 */
public final class DiagnosisEventV2Builder {
    public static final String TOPIC = "dpom.diagnosis-event.v2";
    private static final String SERVICE = "DPOMBaseMCPServer";
    private final ObjectMapper mapper;

    /**
     * Creates a builder.
     * @param mapper JSON mapper
     */
    public DiagnosisEventV2Builder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Builds and freezes canonical event content.
     * @param source persisted source facts
     * @return frozen publication
     * @throws IllegalArgumentException when facts or content violate the contract
     */
    public FrozenPublication build(DiagnosisEventSource source) {
        validate(source);
        Investigation aggregate = source.investigation();
        PublicationIntentRequest intent = source.intent();
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", intent.eventId());
        root.put("eventType", "investigation.completed");
        root.put("schemaVersion", "2.0");
        root.put("occurredAt", intent.createdAt().toString());
        root.set("producer", producer(source.producerInstanceId()));
        root.set("sourceAuthority", authority(intent));
        root.put("incidentId", aggregate.incidentId());
        root.put("investigationId", aggregate.investigationId());
        root.put("runId", intent.runId());
        root.put("aggregateSequence", intent.aggregateSequence());
        root.put("idempotencyKey", aggregate.investigationId() + ".completed." + aggregate.version());
        root.set("provenance", source.provenance().deepCopy());
        root.set("evidenceManifest", source.evidenceManifest().deepCopy());
        root.set(source.artifactPayload() ? "artifactRef" : "inlinePayload", source.payload().deepCopy());
        if (!source.artifactPayload() && CanonicalJson.utf8Bytes(source.payload()) > 16_384) {
            throw new IllegalArgumentException("inline payload exceeds contract bound");
        }
        CanonicalRecord canonical = CanonicalJson.encode(mapper, root);
        return new FrozenPublication(intent.intentId(), intent.eventId(), aggregate.investigationId(),
                TOPIC, intent.aggregateSequence(), aggregate.version(), intent.authorityEpoch().epoch(),
                canonical.bytes(), canonical.sha256(), intent.createdAt());
    }

    private ObjectNode producer(String instanceId) {
        ObjectNode value = mapper.createObjectNode();
        value.put("service", SERVICE);
        value.put("instanceId", instanceId);
        return value;
    }

    private ObjectNode authority(PublicationIntentRequest intent) {
        ObjectNode value = mapper.createObjectNode();
        value.put("service", SERVICE);
        value.put("authorityEpoch", intent.authorityEpoch().epoch());
        value.put("aggregateVersion", intent.aggregateVersion());
        value.put("publicationIntentId", intent.intentId());
        return value;
    }

    private static void validate(DiagnosisEventSource source) {
        if (source == null || source.investigation() == null || source.conclusion() == null
                || source.intent() == null || source.provenance() == null
                || source.evidenceManifest() == null || source.payload() == null) {
            throw new IllegalArgumentException("persisted event source incomplete");
        }
        Investigation aggregate = source.investigation();
        PublicationIntentRequest intent = source.intent();
        boolean eligible = aggregate.status() == InvestigationStatus.COMPLETED
                || aggregate.status() == InvestigationStatus.INCONCLUSIVE;
        if (!eligible || aggregate.version() != intent.aggregateVersion()
                || !aggregate.investigationId().equals(intent.investigationId())
                || !aggregate.investigationId().equals(source.conclusion().investigationId())
                || !SERVICE.equals(intent.authorityEpoch().service())
                || source.producerInstanceId() == null || source.producerInstanceId().isBlank()) {
            throw new IllegalArgumentException("event source identity/authority mismatch");
        }
    }
}
