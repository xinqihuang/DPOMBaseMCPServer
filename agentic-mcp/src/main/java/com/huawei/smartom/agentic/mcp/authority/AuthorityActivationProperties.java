/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.authority;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.util.List;

/**
 * Phase 1B 源权威激活所需的部署事实。
 *
 * @param enabled 是否请求激活 DPOMBase 权威
 * @param authorityEpoch 新权威纪元
 * @param cutoverAt 切换时间
 * @param producerIdentity 生产者实例身份
 * @param acceptedSchemas 允许发布的 schema
 * @param characterizationAccepted Phase 1A 表征是否通过
 * @param dataCompatibilityAccepted 数据兼容是否通过
 * @param dualPathParityAccepted 双路径对等是否通过
 * @param rollbackAccepted 回滚演练是否通过
 * @param retirementCriteriaRecorded 退役标准是否记录
 * @param dpomAgentAdmissionStopped 旧权威是否停止新建
 * @param dpomAgentDrainedOrClosed 旧权威在途是否排空或显式关闭
 * @author Codex
 * @since 2026-08-25
 */
@ConfigurationProperties("dpom.investigation.authority")
public record AuthorityActivationProperties(boolean enabled, String authorityEpoch, Instant cutoverAt,
        String producerIdentity, List<String> acceptedSchemas, boolean characterizationAccepted,
        boolean dataCompatibilityAccepted, boolean dualPathParityAccepted, boolean rollbackAccepted,
        boolean retirementCriteriaRecorded, boolean dpomAgentAdmissionStopped,
        boolean dpomAgentDrainedOrClosed) {
    /**
     * 激活请求缺少任一客观先决条件时失败关闭。
     */
    public void validateActivation() {
        if (!enabled) {
            return;
        }
        require(authorityEpoch != null && !authorityEpoch.isBlank(), "MISSING_AUTHORITY_EPOCH");
        require(cutoverAt != null, "MISSING_CUTOVER_TIMESTAMP");
        require(producerIdentity != null && !producerIdentity.isBlank(), "MISSING_PRODUCER_IDENTITY");
        require(acceptedSchemas != null && acceptedSchemas.containsAll(
                List.of("diagnosis-event/2.0", "diagnosis-progress/1.0")), "INCOMPLETE_SCHEMA_MATRIX");
        require(characterizationAccepted && dataCompatibilityAccepted && dualPathParityAccepted,
                "INCOMPLETE_PARITY_EVIDENCE");
        require(rollbackAccepted && retirementCriteriaRecorded, "INCOMPLETE_ROLLBACK_EVIDENCE");
        require(dpomAgentAdmissionStopped && dpomAgentDrainedOrClosed, "SPLIT_AUTHORITY_RISK");
    }

    private void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }
}
