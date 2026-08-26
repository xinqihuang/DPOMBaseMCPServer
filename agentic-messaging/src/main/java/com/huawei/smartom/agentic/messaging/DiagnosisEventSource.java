/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;

/**
 * Persisted terminal facts required to freeze a Diagnosis Event v2.
 * @param investigation terminal aggregate
 * @param conclusion immutable conclusion
 * @param intent durable publication intent
 * @param producerInstanceId bounded producer identity
 * @param provenance version provenance
 * @param evidenceManifest evidence manifest reference
 * @param payload inline payload or artifact reference
 * @param artifactPayload whether payload is an artifact reference
 * @author Codex
 * @since 2026-08-25
 */
public record DiagnosisEventSource(Investigation investigation, Conclusion conclusion,
                                   PublicationIntentRequest intent, String producerInstanceId,
                                   JsonNode provenance, JsonNode evidenceManifest,
                                   JsonNode payload, boolean artifactPayload) {
}
