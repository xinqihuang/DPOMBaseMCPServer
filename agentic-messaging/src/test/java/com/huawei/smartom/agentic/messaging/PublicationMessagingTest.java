/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressStatus;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;
import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;
import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract, retry, Kafka, and replay tests for Phase 1B publication. */
class PublicationMessagingTest {
    private static final Instant NOW = Instant.parse("2026-08-25T02:30:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsExactSharedDiagnosisEventFixtureCanonicalBytes() throws Exception {
        JsonNode fixture = fixture("diagnosis-event/v2/fixtures/valid/terminal-inline.json");
        AuthorityEpoch authority = new AuthorityEpoch("DPOMBaseMCPServer",
                "phase1b-cn-north-9-20260825", NOW.minusSeconds(60));
        Investigation aggregate = new Investigation("INV-20260825-0001", "INC-20260825-0001",
                InvestigationStatus.COMPLETED, 12, budget(), authority, "RUN-20260825-0002", NOW);
        Conclusion conclusion = new Conclusion("CON-20260825-0001", aggregate.investigationId(),
                ConclusionType.ROOT_CAUSE_IDENTIFIED, "ROOT_CAUSE_IDENTIFIED",
                List.of("EV-TRACE-0007", "EV-CODE-0011"), NOW);
        PublicationIntentRequest intent = new PublicationIntentRequest(
                "PUB-INV-20260825-0001-12", fixture.path("eventId").asText(),
                aggregate.investigationId(), aggregate.activeRunId(), 12, 1, authority, NOW);
        DiagnosisEventSource source = new DiagnosisEventSource(aggregate, conclusion, intent,
                fixture.at("/producer/instanceId").asText(), fixture.path("provenance"),
                fixture.path("evidenceManifest"), fixture.path("inlinePayload"), false);

        FrozenPublication result = new DiagnosisEventV2Builder(mapper).build(source);
        assertThat(result.canonicalBytes()).isEqualTo(new JsonCanonicalizer(
                mapper.writeValueAsBytes(fixture)).getEncodedUTF8());
        assertThat(result.canonicalSha256())
                .isEqualTo("7d41ba588c33060d7a48481dbac060940a1945a128c69305e47c1c9acccb48d2");
    }

    @Test
    void buildsExactSharedProgressFixtureCanonicalBytes() throws Exception {
        JsonNode fixture = fixture("diagnosis-progress/v1/fixtures/valid/running.json");
        ProgressRecord progress = new ProgressRecord(fixture.path("progressId").asText(),
                fixture.path("investigationId").asText(), fixture.path("runId").asText(), 4, 8,
                ProgressStatus.RUNNING, "HYPOTHESIS", "HYPOTHESIS_RANKED",
                Instant.parse(fixture.path("occurredAt").asText()));
        AuthorityEpoch authority = new AuthorityEpoch("DPOMBaseMCPServer",
                fixture.at("/sourceAuthority/authorityEpoch").asText(), NOW.minusSeconds(60));

        FrozenPublication result = new ProgressV1Builder(mapper).build(progress, authority, 60,
                "CP-INV-20260825-0001-4");
        assertThat(result.canonicalBytes()).isEqualTo(new JsonCanonicalizer(
                mapper.writeValueAsBytes(fixture)).getEncodedUTF8());
    }

    @Test
    void publishesOnlyFixedTopicWithInvestigationKeyAndBoundedHeaders() {
        MockProducer<String, byte[]> producer = new MockProducer<>(true,
                new StringSerializer(), new ByteArraySerializer());
        KafkaCanonicalPublisher publisher = new KafkaCanonicalPublisher(producer,
                "dpom-base-instance-1", Duration.ofSeconds(1));
        FrozenPublication frozen = frozen();

        publisher.publish(new PublicationLease(frozen, "fence-1", 1, NOW.plusSeconds(30)));

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().getFirst().topic()).isEqualTo(DiagnosisEventV2Builder.TOPIC);
        assertThat(producer.history().getFirst().key()).isEqualTo("inv-1");
        assertThat(producer.history().getFirst().headers()).hasSize(5);
    }

    @Test
    void workerUsesFencedRetryAndEventuallyTerminalizesWithoutRegeneration() {
        InMemoryStore store = new InMemoryStore(frozen());
        PublicationPolicy policy = new PublicationPolicy(10, 2, Duration.ofHours(1),
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(5), 100);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PublicationWorker worker = new PublicationWorker(store, lease -> {
            throw new IllegalStateException("broker unavailable with unsafe detail");
        }, policy, clock, "worker-1");

        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(store.failureCode).isEqualTo("TRANSIENT_DELIVERY_FAILURE");
        store.leased = false;
        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(store.failureCode).isEqualTo("DELIVERY_ATTEMPTS_EXHAUSTED");
        assertThat(store.original.canonicalSha256()).isEqualTo(frozen().canonicalSha256());
    }

    @Test
    void replayRequiresAuthenticationAndAcceptsNoReplacementContent() {
        InMemoryStore store = new InMemoryStore(frozen());
        OperatorReplayService replay = new OperatorReplayService(store,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> replay.request(false, "intent-1", "operator-1", "BROKER_RECOVERED"))
                .isInstanceOf(SecurityException.class);
        assertThat(replay.request(true, "intent-1", "operator-1", "BROKER_RECOVERED")).isTrue();
        assertThat(store.operatorRef).isEqualTo("operator-1");
        assertThat(store.original.canonicalBytes()).isEqualTo(frozen().canonicalBytes());
    }

    @Test
    void validatesStartupSafelyAndExposesCapacityWithoutHighCardinalityLabels() {
        PublicationProperties invalid = new PublicationProperties(true, "", "secret-producer",
                "worker-1", 10, 3, Duration.ofHours(1), Duration.ofSeconds(30),
                Duration.ofSeconds(1), Duration.ofMinutes(1), 100, Duration.ofSeconds(5),
                Duration.ofSeconds(1));
        assertThatThrownBy(() -> new PublicationConfiguration().publicationPolicy(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid publication configuration")
                .hasMessageNotContaining("secret-producer");

        InMemoryStore store = new InMemoryStore(frozen());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new PublicationObservability(registry, store, 1);
        assertThat(new PublicationReadiness(store, 1).health().getDetails())
                .containsEntry("state", "CAPACITY_EXHAUSTED");
        assertThat(new PublicationAdmissionService(store, 1).admit(frozen()))
                .isEqualTo(PublicationAdmissionOutcome.CAPACITY_EXHAUSTED);
        assertThat(registry.get("dpom.publication.backlog").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("dpom.publication.outcome").counters()).hasSize(3);
    }

    private JsonNode fixture(String relative) throws Exception {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("contracts"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new IllegalStateException("shared contracts root not found");
        }
        return mapper.readTree(Files.readAllBytes(cursor.resolve("contracts").resolve(relative)));
    }

    private InvestigationBudget budget() {
        return new InvestigationBudget(10, 10, 1000, 300, 1, 1, 100, 30);
    }

    private FrozenPublication frozen() {
        return new FrozenPublication("intent-1", "event-1", "inv-1",
                DiagnosisEventV2Builder.TOPIC, 1, 2, "epoch-1", "{}".getBytes(),
                "a".repeat(64), NOW);
    }

    private static final class InMemoryStore implements PublicationDeliveryPort {
        private final FrozenPublication original;
        private int attempt;
        private boolean leased;
        private String failureCode;
        private String operatorRef;

        private InMemoryStore(FrozenPublication original) {
            this.original = original;
        }

        @Override
        public boolean freeze(FrozenPublication publication) {
            return false;
        }

        @Override
        public List<PublicationLease> leaseEligible(String owner, Instant now, int limit,
                                                     Duration leaseDuration, int maxAttempts,
                                                     Duration maxAge) {
            if (leased || attempt >= maxAttempts) {
                return List.of();
            }
            leased = true;
            attempt++;
            return List.of(new PublicationLease(original, "fence-" + attempt, attempt,
                    now.plus(leaseDuration)));
        }

        @Override
        public boolean acknowledge(String intentId, String fencingToken, Instant acknowledgedAt) {
            return true;
        }

        @Override
        public boolean recordFailure(String intentId, String fencingToken, Instant retryAt,
                                     boolean terminal, String reasonCode) {
            failureCode = reasonCode;
            return true;
        }

        @Override
        public boolean requestReplay(String intentId, String requestedOperatorRef,
                                     String reasonCode, Instant requestedAt) {
            operatorRef = requestedOperatorRef;
            return original.intentId().equals(intentId);
        }

        @Override
        public long pendingCount() {
            return 1;
        }
    }
}
