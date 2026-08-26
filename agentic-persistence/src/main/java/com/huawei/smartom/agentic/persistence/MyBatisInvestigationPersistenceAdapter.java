/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressWindow;
import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;
import com.huawei.smartom.agentic.diagnosis.port.AuditPort;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationTransactionPort;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;
import com.huawei.smartom.agentic.diagnosis.port.PublicationDeliveryPort;
import com.huawei.smartom.agentic.diagnosis.port.PublicationIntentPort;
import com.huawei.smartom.agentic.diagnosis.port.TerminalCommit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 服务本地 Investigation MyBatis 持久化与事务适配器。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Repository
@ConditionalOnProperty(prefix = "dpom.investigation.persistence", name = "enabled", havingValue = "true")
public class MyBatisInvestigationPersistenceAdapter implements InvestigationRepository,
        InvestigationTransactionPort, ProgressPort, PublicationIntentPort, AuditPort,
        PublicationDeliveryPort {

    private final InvestigationMapper mapper;

    /**
     * 创建持久化适配器。
     *
     * @param mapper Investigation 映射器
     */
    public MyBatisInvestigationPersistenceAdapter(InvestigationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Investigation> find(String investigationId) {
        return mapper.find(investigationId).map(InvestigationRow::toDomain);
    }

    /**
     * 原子插入聚合与预算。
     *
     * @param investigation 调查聚合
     * @return 两行均插入返回 true
     */
    @Override
    @Transactional
    public boolean insert(Investigation investigation) {
        return mapper.insertAggregate(investigation) == 1 && mapper.insertBudget(investigation) == 1;
    }

    /**
     * 使用乐观版本原子更新聚合与预算。
     *
     * @param investigation 新聚合
     * @param expectedVersion 预期旧版本
     * @return 更新成功返回 true
     */
    @Override
    @Transactional
    public boolean update(Investigation investigation, long expectedVersion) {
        if (mapper.updateAggregate(investigation, expectedVersion) != 1) {
            return false;
        }
        return mapper.updateBudget(investigation) == 1;
    }

    /**
     * 在一个事务中提交聚合终态与所有派生事实。
     *
     * @param commit 终态提交
     * @return 乐观更新成功返回 true
     */
    @Override
    @Transactional
    public boolean commitTerminal(TerminalCommit commit) {
        if (mapper.updateAggregate(commit.investigation(), commit.expectedVersion()) != 1) {
            return false;
        }
        requireOne(mapper.updateBudget(commit.investigation()));
        if (commit.conclusion() != null) {
            requireOne(mapper.insertConclusion(ConclusionRow.from(commit.conclusion())));
        }
        requireOne(mapper.insertProgress(commit.progress()));
        requireOne(mapper.insertAudit(commit.audit()));
        if (commit.publicationIntent() != null) {
            requireOne(mapper.insertPublicationIntent(commit.publicationIntent()));
        }
        return true;
    }

    @Override
    public void append(ProgressRecord progress) {
        requireOne(mapper.insertProgress(progress));
    }

    @Override
    public List<ProgressRecord> after(String investigationId, long sequenceExclusive, int limit) {
        return mapper.progressAfter(investigationId, sequenceExclusive, Math.min(Math.max(limit, 1), 200));
    }

    @Override
    public ProgressWindow window(String investigationId, long sequenceExclusive, int limit) {
        long oldest = optionalSequence(mapper.oldestProgressSequence(investigationId));
        long latest = optionalSequence(mapper.latestProgressSequence(investigationId));
        return new ProgressWindow(oldest, latest, after(investigationId, sequenceExclusive, limit));
    }

    @Override
    public boolean append(PublicationIntentRequest request) {
        return mapper.insertPublicationIntent(request) == 1;
    }

    @Override
    public void append(AuditRecord record) {
        requireOne(mapper.insertAudit(record));
    }

    @Override
    public boolean freeze(FrozenPublication publication) {
        return mapper.freezePublication(publication) == 1;
    }

    @Override
    @Transactional
    public List<PublicationLease> leaseEligible(String owner, Instant now, int limit,
                                                 Duration leaseDuration, int maxAttempts,
                                                 Duration maxAge) {
        int boundedLimit = Math.min(Math.max(limit, 1), 1000);
        List<PublicationRow> candidates = mapper.selectLeaseCandidates(now, now.minus(maxAge),
                maxAttempts, boundedLimit);
        List<PublicationLease> leases = new ArrayList<>();
        for (PublicationRow candidate : candidates) {
            String token = UUID.randomUUID().toString();
            Instant leaseUntil = now.plus(leaseDuration);
            if (mapper.claimPublication(candidate.intentId(), owner, token, now, leaseUntil) == 1) {
                leases.add(new PublicationLease(candidate.toDomain(), token,
                        candidate.attemptCount() + 1, leaseUntil));
            }
        }
        return List.copyOf(leases);
    }

    @Override
    public boolean acknowledge(String intentId, String fencingToken, Instant acknowledgedAt) {
        return mapper.acknowledgePublication(intentId, fencingToken, acknowledgedAt) == 1;
    }

    @Override
    public boolean recordFailure(String intentId, String fencingToken, Instant retryAt,
                                 boolean terminal, String reasonCode) {
        return mapper.failPublication(intentId, fencingToken, retryAt,
                terminal ? "TERMINAL_FAILURE" : "PENDING", reasonCode) == 1;
    }

    @Override
    @Transactional
    public boolean requestReplay(String intentId, String operatorRef, String reasonCode,
                                 Instant requestedAt) {
        Optional<PublicationRow> publication = mapper.findPublication(intentId);
        if (publication.isEmpty() || mapper.replayPublication(intentId, requestedAt) != 1) {
            return false;
        }
        requireOne(mapper.insertReplayAudit("replay-" + UUID.randomUUID(), publication.get(),
                operatorRef, reasonCode, requestedAt));
        return true;
    }

    @Override
    public long pendingCount() {
        return mapper.countPendingPublications();
    }

    private void requireOne(int count) {
        if (count != 1) {
            throw new IllegalStateException("unexpected persistence row count");
        }
    }

    private long optionalSequence(Long sequence) {
        return sequence == null ? 0L : sequence;
    }
}
