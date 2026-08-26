/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressStatus;
import com.huawei.smartom.agentic.diagnosis.model.ProgressWindow;
import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 持久化进度到 Kafka 的序列、状态与重启重投对等测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
class PersistedProgressKafkaPublisherTest {
    @Test
    void publishesCanonicalKafkaRecordFromTheSamePersistedProgress() {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        ProgressRecord record = new ProgressRecord("PROG-9", "INV-1", "RUN-1", 9, 3,
                ProgressStatus.RUNNING, "EVIDENCE", "STEP_COMPLETED", now);
        ProgressPort progress = progress(record);
        var authority = new AuthorityEpoch("DPOMBaseMCPServer", "epoch-2", now);
        var budget = new InvestigationBudget(10, 10, 1000, 60, 0, 0, 0, 0);
        Investigation investigation = new Investigation("INV-1", "INC-1", InvestigationStatus.RUNNING,
                3, budget, authority, "RUN-1", now);
        InvestigationRepository investigations = investigations(investigation);
        AtomicReference<PublicationLease> sent = new AtomicReference<>();
        CanonicalPublisher broker = sent::set;
        var service = new PersistedProgressKafkaPublisher(investigations, progress,
                new ProgressV1Builder(new ObjectMapper()), broker);

        assertThat(service.publishAfter("INV-1", 8, 10)).isEqualTo(new ProgressPublicationBatch(1, 9, false));
        assertThat(sent.get().publication().topic()).isEqualTo("dpom.diagnosis-progress.v1");
        assertThat(sent.get().publication().sequence()).isEqualTo(9);
    }

    private ProgressPort progress(ProgressRecord record) {
        return new ProgressPort() {
            @Override public void append(ProgressRecord value) { throw new UnsupportedOperationException(); }
            @Override public List<ProgressRecord> after(String id, long after, int limit) { return List.of(record); }
            @Override public ProgressWindow window(String id, long after, int limit) {
                return new ProgressWindow(1, 9, List.of(record));
            }
        };
    }

    private InvestigationRepository investigations(Investigation investigation) {
        return new InvestigationRepository() {
            @Override public Optional<Investigation> find(String id) { return Optional.of(investigation); }
            @Override public boolean insert(Investigation value) { throw new UnsupportedOperationException(); }
            @Override public boolean update(Investigation value, long version) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
