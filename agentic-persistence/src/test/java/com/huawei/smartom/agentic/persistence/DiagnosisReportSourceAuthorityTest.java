/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationRun;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 报告源权威只接受摘要正确且版本受支持的持久化事实。 */
class DiagnosisReportSourceAuthorityTest {
    private static final Instant NOW = Instant.parse("2026-08-15T03:56:55Z");

    @Test
    void freezesPersistedFactsWithoutProviderDtoLeakageAndKeepsMissingProvenanceVisible() throws Exception {
        byte[] content = event("2.0");
        var authority = new MyBatisDiagnosisReportSourceAuthority(repository(), mapper(content, digest(content)),
                new ObjectMapper());
        var snapshot = authority.freeze("INV-1");
        assertThat(snapshot.investigation().investigationId()).isEqualTo("INV-1");
        assertThat(snapshot.evidence()).extracting(value -> value.evidenceId()).containsExactly("EVID-1");
        assertThat(snapshot.gapCodes()).containsExactly("MISSING_PROVENANCE");
        assertThat(snapshot.componentVersion()).isEqualTo("1.0.0");
    }

    @Test
    void rejectsDigestMismatchAndUnsupportedSourceMajor() throws Exception {
        byte[] supported = event("2.0");
        assertThatThrownBy(() -> new MyBatisDiagnosisReportSourceAuthority(repository(),
                mapper(supported, "0".repeat(64)), new ObjectMapper()).freeze("INV-1"))
                .hasMessage("REPORT_SOURCE_DIGEST_MISMATCH");
        byte[] unsupported = event("3.0");
        assertThatThrownBy(() -> new MyBatisDiagnosisReportSourceAuthority(repository(),
                mapper(unsupported, digest(unsupported)), new ObjectMapper()).freeze("INV-1"))
                .hasMessage("REPORT_SOURCE_VERSION_UNSUPPORTED");
    }

    private InvestigationRepository repository() {
        Investigation investigation = new Investigation("INV-1", "INC-1", InvestigationStatus.COMPLETED, 2,
                new InvestigationBudget(10, 10, 1000, 3600, 2, 2, 200, 60),
                new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", NOW), "RUN-1", NOW.plusSeconds(60));
        return new InvestigationRepository() {
            @Override public Optional<Investigation> find(String id) { return Optional.of(investigation); }
            @Override public boolean insert(Investigation value) { throw new UnsupportedOperationException(); }
            @Override public boolean update(Investigation value, long version) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private DiagnosisReportSourceMapper mapper(byte[] content, String sha256) {
        return new DiagnosisReportSourceMapper() {
            @Override public Optional<InvestigationRun> latestRun(String id) {
                return Optional.of(new InvestigationRun("RUN-1", "INV-1", 1, InvestigationStatus.COMPLETED, 2,
                        NOW, NOW.plusSeconds(60)));
            }
            @Override public List<Observation> observations(String id) {
                return List.of(new Observation("OBS-1", "INV-1", "EVID-1", "OBSERVED", NOW));
            }
            @Override public List<HypothesisRow> hypotheses(String id) {
                return List.of(new HypothesisRow("HYP-1", "INV-1", "CAUSE", "SUPPORTED", "EVID-1", NOW));
            }
            @Override public Optional<ConclusionRow> conclusion(String id) {
                return Optional.of(new ConclusionRow("CON-1", "INV-1", "ROOT_CAUSE_IDENTIFIED",
                        "CAUSE", "EVID-1", NOW.plusSeconds(60)));
            }
            @Override public Optional<PublicationSourceRow> latestPublication(String id) {
                return Optional.of(new PublicationSourceRow("EVENT-1", "RUN-1", content, sha256,
                        NOW.plusSeconds(60)));
            }
        };
    }

    private byte[] event(String version) {
        String value = "{\"eventType\":\"investigation.completed\",\"schemaVersion\":\"" + version
                + "\",\"occurredAt\":\"2026-08-15T03:56:55Z\",\"evidenceManifest\":{"
                + "\"manifestId\":\"MANIFEST-1\",\"sha256\":\"" + "a".repeat(64)
                + "\",\"byteSize\":128}}";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String digest(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
