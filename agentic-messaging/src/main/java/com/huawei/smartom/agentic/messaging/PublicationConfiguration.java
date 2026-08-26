/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.Properties;

/**
 * Explicitly enabled Kafka publication composition.
 * @author Codex
 * @since 2026-08-25
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(PublicationProperties.class)
@ConditionalOnProperty(prefix = "dpom.investigation.publication", name = "enabled", havingValue = "true")
public class PublicationConfiguration {

    /**
     * Creates the bounded policy and validates all required configuration.
     * @param value configuration properties
     * @return bounded policy
     */
    @Bean
    public PublicationPolicy publicationPolicy(PublicationProperties value) {
        requireText(value.bootstrapServers());
        requireText(value.producerIdentity());
        requireText(value.workerId());
        require(value.acknowledgementTimeout());
        require(value.pollDelay());
        return new PublicationPolicy(value.batchSize(), value.maxAttempts(), value.maxAge(),
                value.leaseDuration(), value.initialBackoff(), value.maxBackoff(), value.capacity());
    }

    /**
     * Creates a producer configured for recoverable at-least-once delivery.
     * @param value configuration properties
     * @return Kafka producer
     */
    @Bean(destroyMethod = "close")
    public Producer<String, byte[]> diagnosisKafkaProducer(PublicationProperties value) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, value.bootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, value.producerIdentity());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 70_000);
        return new KafkaProducer<>(config);
    }

    /**
     * Creates the canonical Kafka adapter.
     * @param producer Kafka producer
     * @param value configuration properties
     * @return canonical publisher
     */
    @Bean
    public CanonicalPublisher canonicalPublisher(Producer<String, byte[]> producer,
                                                  PublicationProperties value) {
        return new KafkaCanonicalPublisher(producer, value.producerIdentity(),
                value.acknowledgementTimeout());
    }

    /**
     * Creates contract builders.
     * @param mapper application JSON mapper
     * @return Diagnosis Event builder
     */
    @Bean
    public DiagnosisEventV2Builder diagnosisEventV2Builder(ObjectMapper mapper) {
        return new DiagnosisEventV2Builder(mapper);
    }

    /**
     * Creates the progress builder.
     * @param mapper application JSON mapper
     * @return progress builder
     */
    @Bean
    public ProgressV1Builder progressV1Builder(ObjectMapper mapper) {
        return new ProgressV1Builder(mapper);
    }

    /**
     * 创建从同一持久化进度日志读取的 Kafka 投影器。
     * @param investigations 权威调查仓储
     * @param progress 权威进度日志
     * @param builder Progress v1 构造器
     * @param publisher Kafka 发布边界
     * @return 进度投影器
     */
    @Bean
    public PersistedProgressKafkaPublisher persistedProgressKafkaPublisher(
            InvestigationRepository investigations, ProgressPort progress,
            ProgressV1Builder builder, CanonicalPublisher publisher) {
        return new PersistedProgressKafkaPublisher(investigations, progress, builder, publisher);
    }

    /**
     * Registers safe metrics.
     * @param registry meter registry
     * @param store durable store
     * @param value configuration properties
     * @return observability component
     */
    @Bean
    public PublicationObservability publicationObservability(MeterRegistry registry,
                                                               PublicationDeliveryPort store,
                                                               PublicationProperties value) {
        return new PublicationObservability(registry, store, value.capacity());
    }

    /**
     * Creates capacity-bounded freeze admission.
     * @param store durable store
     * @param value configuration properties
     * @return admission service
     */
    @Bean
    public PublicationAdmissionService publicationAdmissionService(PublicationDeliveryPort store,
                                                                     PublicationProperties value) {
        return new PublicationAdmissionService(store, value.capacity());
    }

    /**
     * Creates the post-commit worker.
     * @param store durable store
     * @param publisher Kafka adapter
     * @param policy bounded policy
     * @param observability safe metrics
     * @param value configuration properties
     * @return publication worker
     */
    @Bean
    public PublicationWorker publicationWorker(PublicationDeliveryPort store,
                                                CanonicalPublisher publisher,
                                                PublicationPolicy policy,
                                                PublicationObservability observability,
                                                PublicationProperties value) {
        return new PublicationWorker(store, publisher, policy, Clock.systemUTC(), value.workerId(),
                observability);
    }

    /**
     * Creates scheduler.
     * @param worker publication worker
     * @return scheduler
     */
    @Bean
    public PublicationScheduler publicationScheduler(PublicationWorker worker) {
        return new PublicationScheduler(worker);
    }

    /**
     * Creates capacity readiness.
     * @param store durable store
     * @param value configuration properties
     * @return readiness indicator
     */
    @Bean
    public PublicationReadiness publicationReadiness(PublicationDeliveryPort store,
                                                      PublicationProperties value) {
        return new PublicationReadiness(store, value.capacity());
    }

    /**
     * Creates authenticated replay admission.
     * @param store durable store
     * @return replay service
     */
    @Bean
    public OperatorReplayService operatorReplayService(PublicationDeliveryPort store) {
        return new OperatorReplayService(store, Clock.systemUTC());
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalStateException("invalid publication configuration");
        }
    }

    private static void require(java.time.Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("invalid publication configuration");
        }
    }
}
