/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Kafka adapter for the two fixed Phase 1B topics.
 * @author Codex
 * @since 2026-08-25
 */
public final class KafkaCanonicalPublisher implements CanonicalPublisher, AutoCloseable {
    private static final Set<String> TOPICS = Set.of(DiagnosisEventV2Builder.TOPIC, ProgressV1Builder.TOPIC);
    private final Producer<String, byte[]> producer;
    private final String producerIdentity;
    private final Duration acknowledgementTimeout;

    /**
     * Creates the adapter.
     * @param producer configured Kafka producer
     * @param producerIdentity bounded producer identity
     * @param acknowledgementTimeout broker acknowledgement timeout
     */
    public KafkaCanonicalPublisher(Producer<String, byte[]> producer, String producerIdentity,
                                   Duration acknowledgementTimeout) {
        this.producer = producer;
        this.producerIdentity = bounded(producerIdentity, 128, "producerIdentity");
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Override
    public void publish(PublicationLease lease) {
        FrozenPublication publication = lease.publication();
        if (!TOPICS.contains(publication.topic())) {
            throw new IllegalArgumentException("unsupported publication topic");
        }
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(publication.topic(), null,
                publication.investigationId(), publication.canonicalBytes(), java.util.List.of(
                    header("content-type", "application/json"),
                    header("schema-version", publication.topic().endsWith(".v2") ? "2.0" : "1.0"),
                    header("producer", producerIdentity),
                    header("canonical-sha256", publication.canonicalSha256()),
                    header("publication-intent-id", publication.intentId())
                ));
        try {
            producer.send(record).get(acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication interrupted", exception);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Kafka publication failed", exception);
        }
    }

    @Override
    public void close() {
        producer.close(Duration.ofSeconds(5));
    }

    private static RecordHeader header(String name, String value) {
        return new RecordHeader(name, bounded(value, 256, name).getBytes(StandardCharsets.UTF_8));
    }

    private static String bounded(String value, int maximum, String name) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(name);
        }
        return value;
    }
}
