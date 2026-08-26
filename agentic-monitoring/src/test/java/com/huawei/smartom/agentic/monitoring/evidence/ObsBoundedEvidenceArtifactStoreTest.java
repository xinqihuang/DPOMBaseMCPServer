/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OBS 有界证据 Artifact 存储测试。
 *
 * @author Codex
 * @since 2026-08-26
 */
class ObsBoundedEvidenceArtifactStoreTest {

    private ObsEvidenceAdapter adapter;
    private ObsProperties properties;
    private ObsBoundedEvidenceArtifactStore store;

    @BeforeEach
    void setUp() {
        adapter = mock(ObsEvidenceAdapter.class);
        properties = new ObsProperties();
        properties.setEnabled(true);
        properties.setEndpoint("https://obs.example.invalid");
        properties.setBucket("runtime-bucket");
        properties.setPrefix("runtime-prefix");
        properties.setServiceCode("dpom-base");
        properties.setMaxBytes(1024);
        store = new ObsBoundedEvidenceArtifactStore(adapter, properties, new ObjectMapper());
        when(adapter.putEvidence(any())).thenAnswer(invocation -> {
            ObsPutEvidenceRequest request = invocation.getArgument(0);
            return new ObsPutEvidenceResponse(request.objectKey(), "etag", request.content().length);
        });
    }

    @Test
    void canonicalContentProducesDeterministicObjectIdentity() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        StoredEvidence left = store.store("INV-1", "APM_TRACES", first, Instant.EPOCH);
        StoredEvidence right = store.store("INV-1", "APM_TRACES", second, Instant.EPOCH);

        assertThat(left).isEqualTo(right);
        assertThat(left.sourceRef()).startsWith("obs://runtime-bucket/runtime-prefix/dpom-base/INV-1/APM_TRACES/")
                .endsWith(".json");
        ArgumentCaptor<ObsPutEvidenceRequest> captor = ArgumentCaptor.forClass(ObsPutEvidenceRequest.class);
        verify(adapter, org.mockito.Mockito.times(2)).putEvidence(captor.capture());
        assertThat(captor.getAllValues().get(0).content())
                .isEqualTo(captor.getAllValues().get(1).content());
        assertThat(captor.getValue().contentType()).isEqualTo("application/json");
    }

    @Test
    void unsafeIdentityIsRejectedBeforeObsCall() {
        Throwable throwable = catchThrowable(() ->
                store.store("../INV", "APM_TRACES", Map.of("a", 1), Instant.EPOCH));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).putEvidence(any());
    }

    @Test
    void oversizedCanonicalContentIsRejectedBeforeObsCall() {
        properties.setMaxBytes(8);

        Throwable throwable = catchThrowable(() ->
                store.store("INV-1", "APM_TRACES", Map.of("payload", "too-large"), Instant.EPOCH));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).putEvidence(any());
    }
}
