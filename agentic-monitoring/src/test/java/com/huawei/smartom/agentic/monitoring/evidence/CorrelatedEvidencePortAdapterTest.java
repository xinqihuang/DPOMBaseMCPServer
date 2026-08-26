package com.huawei.smartom.agentic.monitoring.evidence;

import com.huawei.smartom.agentic.diagnosis.port.EvidenceRequest;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateBranchResult;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentResponse;
import com.huawei.smartom.agentic.monitoring.correlate.CorrelateIncidentService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelatedEvidencePortAdapterTest {

    @Test
    void mapsBoundedProviderResultsToNeutralArtifactReferences() {
        var service = mock(CorrelateIncidentService.class);
        when(service.correlate(any())).thenReturn(new CorrelateIncidentResponse(
                CorrelateBranchResult.success(Map.of("alarm", "bounded")),
                CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.success(Map.of("trace", "bounded")),
                CorrelateBranchResult.ofSkipped()));
        BoundedEvidenceArtifactStore store = (investigationId, type, value, capturedAt) ->
                new StoredEvidence("controlled/" + investigationId + "/" + type,
                        type.equals("CES_ALARMS") ? "a".repeat(64) : "b".repeat(64), 128);
        var adapter = new CorrelatedEvidencePortAdapter(service, store);
        var request = new EvidenceRequest("INV-1", "asset-service",
                Instant.parse("2026-08-25T01:00:00Z"), Instant.parse("2026-08-25T02:00:00Z"),
                10, "CORRELATED");

        assertThat(adapter.collect(request)).hasSize(2)
                .allSatisfy(value -> {
                    assertThat(value.sourceRef()).startsWith("controlled/INV-1/");
                    assertThat(value.sha256()).matches("[0-9a-f]{64}");
                });
    }

    @Test
    void uploadFailureCannotCreateDanglingEvidenceReference() {
        var service = mock(CorrelateIncidentService.class);
        when(service.correlate(any())).thenReturn(new CorrelateIncidentResponse(
                CorrelateBranchResult.success(Map.of("alarm", "bounded")),
                CorrelateBranchResult.ofSkipped(), CorrelateBranchResult.ofSkipped(),
                CorrelateBranchResult.ofSkipped()));
        BoundedEvidenceArtifactStore store = mock(BoundedEvidenceArtifactStore.class);
        when(store.store(any(), any(), any(), any())).thenThrow(new IllegalStateException("upload failed"));
        var adapter = new CorrelatedEvidencePortAdapter(service, store);
        var request = new EvidenceRequest("INV-1", "asset-service",
                Instant.parse("2026-08-25T01:00:00Z"), Instant.parse("2026-08-25T02:00:00Z"),
                10, "CORRELATED");

        var throwable = org.assertj.core.api.Assertions.catchThrowable(() -> adapter.collect(request));

        assertThat(throwable).isInstanceOf(IllegalStateException.class);
        verify(store).store(any(), any(), any(), any());
        verify(store, never()).store(any(), org.mockito.ArgumentMatchers.eq("APM_TRACES"), any(), any());
    }
}
