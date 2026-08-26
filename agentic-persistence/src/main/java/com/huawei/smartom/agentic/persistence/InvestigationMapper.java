/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * Investigation 聚合与原子终态事实的 MyBatis 映射器。
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface InvestigationMapper {

    /**
     * 插入聚合头。
     * @param value 聚合
     * @return 影响行数
     */
    int insertAggregate(Investigation value);

    /**
     * 插入聚合预算。
     * @param value 聚合及预算
     * @return 影响行数
     */
    int insertBudget(Investigation value);

    /**
     * 读取聚合和预算。
     * @param investigationId 调查身份
     * @return 聚合连接行
     */
    Optional<InvestigationRow> find(String investigationId);

    /**
     * 乐观更新聚合头。
     * @param value 新聚合
     * @param expectedVersion 预期版本
     * @return 影响行数
     */
    int updateAggregate(@Param("value") Investigation value, @Param("expectedVersion") long expectedVersion);

    /**
     * 更新聚合预算。
     * @param value 新聚合及预算
     * @return 影响行数
     */
    int updateBudget(Investigation value);

    /**
     * 插入不可变结论。
     * @param value 结论行
     * @return 影响行数
     */
    int insertConclusion(ConclusionRow value);

    /**
     * 插入不可变进度。
     * @param value 进度
     * @return 影响行数
     */
    int insertProgress(ProgressRecord value);

    /**
     * 插入追加式审计。
     * @param value 审计
     * @return 影响行数
     */
    int insertAudit(AuditRecord value);

    /**
     * 插入不可变发布意图。
     * @param value 发布意图
     * @return 影响行数
     */
    int insertPublicationIntent(PublicationIntentRequest value);

    /**
     * 查询排他序号后的进度。
     *
     * @param investigationId 调查身份
     * @param sequenceExclusive 排他序号
     * @param limit 最大行数
     * @return 有序进度
     */
    List<ProgressRecord> progressAfter(@Param("investigationId") String investigationId,
                                       @Param("sequenceExclusive") long sequenceExclusive,
                                       @Param("limit") int limit);

    /**
     * @param investigationId 调查身份
     * @return 当前保留的最早进度序号，空日志为 null
     */
    Long oldestProgressSequence(String investigationId);

    /**
     * @param investigationId 调查身份
     * @return 当前最新进度序号，空日志为 null
     */
    Long latestProgressSequence(String investigationId);

    /**
     * Freezes canonical content once.
     * @param value frozen record
     * @return affected rows
     */
    int freezePublication(FrozenPublication value);

    /**
     * Selects bounded delivery candidates.
     * @param now decision time
     * @param oldest minimum creation time
     * @param maxAttempts attempt limit
     * @param limit row limit
     * @return candidates
     */
    List<PublicationRow> selectLeaseCandidates(@Param("now") java.time.Instant now,
                                               @Param("oldest") java.time.Instant oldest,
                                               @Param("maxAttempts") int maxAttempts,
                                               @Param("limit") int limit);

    /**
     * Claims a candidate using a new fence.
     * @param intentId intent identity
     * @param owner worker identity
     * @param token fencing token
     * @param now decision time
     * @param leaseUntil expiry time
     * @return affected rows
     */
    int claimPublication(@Param("intentId") String intentId, @Param("owner") String owner,
                         @Param("token") String token, @Param("now") java.time.Instant now,
                         @Param("leaseUntil") java.time.Instant leaseUntil);

    /**
     * Acknowledges a fenced attempt.
     * @param intentId intent identity
     * @param token fencing token
     * @param acknowledgedAt acknowledgement time
     * @return affected rows
     */
    int acknowledgePublication(@Param("intentId") String intentId, @Param("token") String token,
                               @Param("acknowledgedAt") java.time.Instant acknowledgedAt);

    /**
     * Records a fenced delivery failure.
     * @param intentId intent identity
     * @param token fencing token
     * @param retryAt retry time
     * @param state next state
     * @param reasonCode stable reason
     * @return affected rows
     */
    int failPublication(@Param("intentId") String intentId, @Param("token") String token,
                        @Param("retryAt") java.time.Instant retryAt,
                        @Param("state") String state, @Param("reasonCode") String reasonCode);

    /**
     * Re-admits an immutable record.
     * @param intentId intent identity
     * @param requestedAt request time
     * @return affected rows
     */
    int replayPublication(@Param("intentId") String intentId,
                          @Param("requestedAt") java.time.Instant requestedAt);

    /**
     * Finds a publication row.
     * @param intentId intent identity
     * @return persisted row
     */
    Optional<PublicationRow> findPublication(String intentId);

    /**
     * Appends bounded replay audit.
     * @param auditId audit identity
     * @param value publication row
     * @param operatorRef operator reference
     * @param reasonCode replay reason
     * @param occurredAt request time
     * @return affected rows
     */
    int insertReplayAudit(@Param("auditId") String auditId,
                          @Param("value") PublicationRow value,
                          @Param("operatorRef") String operatorRef,
                          @Param("reasonCode") String reasonCode,
                          @Param("occurredAt") java.time.Instant occurredAt);

    /**
     * Counts the active backlog.
     * @return pending and leased rows
     */
    long countPendingPublications();
}
