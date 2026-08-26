/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bounded scheduler that never logs record bodies or exception payloads.
 * @author Codex
 * @since 2026-08-25
 */
public final class PublicationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationScheduler.class);
    private final PublicationWorker worker;

    /**
     * Creates the scheduler.
     * @param worker bounded worker
     */
    public PublicationScheduler(PublicationWorker worker) {
        this.worker = worker;
    }

    /** Runs one bounded batch. */
    @Scheduled(fixedDelayString = "${dpom.investigation.publication.poll-delay:1s}")
    public void publishBatch() {
        try {
            worker.runOnce();
        }
        catch (RuntimeException exception) {
            LOGGER.warn("publication batch failed, reasonCode=WORKER_FAILURE");
        }
    }
}
