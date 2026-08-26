/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Default-off bounded publication configuration.
 * @param enabled explicit activation flag
 * @param bootstrapServers broker endpoints
 * @param producerIdentity bounded producer identity
 * @param workerId bounded worker identity
 * @param batchSize lease batch
 * @param maxAttempts retry attempts
 * @param maxAge record age
 * @param leaseDuration lease duration
 * @param initialBackoff first retry delay
 * @param maxBackoff retry delay cap
 * @param capacity backlog capacity
 * @param acknowledgementTimeout broker acknowledgement timeout
 * @param pollDelay worker poll delay
 * @author Codex
 * @since 2026-08-25
 */
@ConfigurationProperties("dpom.investigation.publication")
public record PublicationProperties(boolean enabled, String bootstrapServers, String producerIdentity,
                                    String workerId, int batchSize, int maxAttempts, Duration maxAge,
                                    Duration leaseDuration, Duration initialBackoff, Duration maxBackoff,
                                    long capacity, Duration acknowledgementTimeout, Duration pollDelay) {
}
