/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.huawei.smartom.agentic.diagnosis.model.PublicationLease;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;

import java.time.Duration;

/**
 * 从 REST/SSE 共用的持久化进度日志生成 Kafka Progress v1 记录。
 *
 * <p>调用方保存最后已确认序号；进程重启后从更早游标重投仍保持相同身份、内容与摘要。</p>
 *
 * @author Codex
 * @since 2026-08-25
 */
public final class PersistedProgressKafkaPublisher {
    private final InvestigationRepository investigations;
    private final ProgressPort progress;
    private final ProgressV1Builder builder;
    private final CanonicalPublisher publisher;

    /**
     * 创建持久化进度 Kafka 投影器。
     *
     * @param investigations 权威调查仓储
     * @param progress 权威进度日志
     * @param builder Progress v1 构造器
     * @param publisher Kafka 发布边界
     */
    public PersistedProgressKafkaPublisher(InvestigationRepository investigations, ProgressPort progress,
                                           ProgressV1Builder builder, CanonicalPublisher publisher) {
        this.investigations = investigations;
        this.progress = progress;
        this.builder = builder;
        this.publisher = publisher;
    }

    /**
     * 从给定已确认游标之后发布一个有界批次。
     *
     * @param investigationId 调查身份
     * @param afterSequence 已确认的排他游标
     * @param limit 批次上限
     * @return 发布批次结果
     * @throws IllegalArgumentException 游标、批次或调查身份无效
     */
    public ProgressPublicationBatch publishAfter(String investigationId, long afterSequence, int limit) {
        if (afterSequence < 0L || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("progress publication cursor");
        }
        var window = progress.window(investigationId, afterSequence, limit);
        if (window.requiresResynchronization(afterSequence)) {
            return new ProgressPublicationBatch(0, afterSequence, true);
        }
        var investigation = investigations.find(investigationId)
                .orElseThrow(() -> new IllegalArgumentException("investigation not found"));
        long cursor = afterSequence;
        for (var record : window.records()) {
            var frozen = builder.build(record, investigation.authorityEpoch(), null, null);
            publisher.publish(new PublicationLease(frozen, "progress-lease-" + record.progressId(), 1,
                    record.occurredAt().plus(Duration.ofMinutes(1))));
            cursor = record.progressSequence();
        }
        return new ProgressPublicationBatch(window.records().size(), cursor, false);
    }
}
