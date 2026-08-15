/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 证据上传审批编排：批准、撤销、原子消费与回滚，统一做字段校验、结构化审计与低基数指标。
 *
 * <p>审批绑定精确身份 + 内容 checksum；approve/revoke 仅由可信控制面（REST）调用，consume/restore 由 put 侧调用；
 * 日志绝不记录 secret/signature/body/reason。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Service
public class ApprovalService {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalService.class);

    private static final String METRIC_PREFIX = "dpom.approval.";

    private final ApprovalStore store;
    private final ApprovalProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * 构造 {@code ApprovalService}。
     *
     * @param store         审批存储
     * @param properties    审批配置
     * @param meterRegistry Micrometer 指标注册表
     */
    public ApprovalService(ApprovalStore store, ApprovalProperties properties, MeterRegistry meterRegistry) {
        this.store = store;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录（或覆盖）一条审批。
     *
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     * @param approverRef     审批人标识
     * @param reason          审批原因
     * @return 已记录的审批
     */
    public ApprovalRecord approve(String serviceCode, String investigationId, String packageId, String sha256,
            String approverRef, String reason) {
        return executeAudited("APPROVE", serviceCode, investigationId, packageId, sha256, () -> {
            validateIdentity(serviceCode, investigationId, packageId);
            String checksum = normalizeChecksum(sha256);
            requireText(approverRef, "approverRef");
            requireText(reason, "reason");
            long now = System.currentTimeMillis();
            ApprovalRecord record = new ApprovalRecord(serviceCode, investigationId, packageId, checksum,
                    approverRef, reason, now + properties.getApprovalTtlSeconds() * 1000L, now);
            store.approve(record);
            return record;
        });
    }

    /**
     * 撤销一条审批。
     *
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     */
    public void revoke(String serviceCode, String investigationId, String packageId, String sha256) {
        executeAudited("REVOKE", serviceCode, investigationId, packageId, sha256, () -> {
            validateIdentity(serviceCode, investigationId, packageId);
            String checksum = normalizeChecksum(sha256);
            if (!store.revoke(serviceCode, investigationId, packageId, checksum)) {
                throw new SmartomException(ErrorCode.APPROVAL_NOT_FOUND, "no matching approval to revoke");
            }
            return null;
        });
    }

    /**
     * 原子消费一条审批（单赢家），不存在或过期抛 {@code UPLOAD_NOT_APPROVED}。
     *
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     * @return 被消费的审批记录
     */
    public ApprovalRecord consume(String serviceCode, String investigationId, String packageId, String sha256) {
        return executeAudited("CONSUME", serviceCode, investigationId, packageId, sha256, () -> {
            ApprovalRecord record = store.consume(serviceCode, investigationId, packageId,
                    normalizeChecksum(sha256));
            if (record == null) {
                throw new SmartomException(ErrorCode.UPLOAD_NOT_APPROVED,
                        "upload requires explicit approval for this package identity and checksum");
            }
            return record;
        });
    }

    /**
     * 回滚一条已消费的审批（上传失败时调用，仅目标键不存在时写入）。
     *
     * @param record 被消费的审批记录
     */
    public void restore(ApprovalRecord record) {
        executeAudited("RESTORE", record.serviceCode(), record.investigationId(), record.packageId(),
                record.sha256(), () -> {
                    store.restore(record);
                    return null;
                });
    }

    private <T> T executeAudited(String eventType, String serviceCode, String investigationId, String packageId,
            String sha256, Supplier<T> action) {
        try {
            T result = action.get();
            logAudit(eventType, "SUCCESS", null, serviceCode, investigationId, packageId, sha256);
            recordMetric(eventType, "success");
            return result;
        }
        catch (SmartomException exception) {
            logAudit(eventType, "FAILURE", exception.getErrorCode().name(), serviceCode, investigationId,
                    packageId, sha256);
            recordMetric(eventType, "failure");
            throw exception;
        }
        catch (RuntimeException exception) {
            logAudit(eventType, "FAILURE", ErrorCode.INTERNAL.name(), serviceCode, investigationId, packageId, sha256);
            recordMetric(eventType, "failure");
            throw exception;
        }
    }

    private void recordMetric(String action, String result) {
        meterRegistry.counter(METRIC_PREFIX + action.toLowerCase(Locale.ROOT), "result", result).increment();
    }

    private void logAudit(String eventType, String result, String errorCode, String serviceCode, String investigationId,
            String packageId, String sha256) {
        LOG.info("approval audit, eventType={}, result={}, errorCode={}, serviceCode={}, investigationId={}, "
                + "packageId={}, sha256={}", eventType, result, errorCode, sanitizeSegment(serviceCode),
                sanitizeSegment(investigationId), sanitizeSegment(packageId), sanitizeChecksum(sha256));
    }

    private String sanitizeSegment(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}") ? value : "INVALID";
    }

    private String sanitizeChecksum(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}") ? value.toLowerCase(Locale.ROOT) : "INVALID";
    }

    private void validateIdentity(String serviceCode, String investigationId, String packageId) {
        validateSegment(serviceCode, "serviceCode");
        validateSegment(investigationId, "investigationId");
        validateSegment(packageId, "packageId");
    }

    private void validateSegment(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]+")) {
            throw new InvalidParamException(name + " must match [a-zA-Z0-9_-]+");
        }
    }

    private String normalizeChecksum(String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new InvalidParamException("sha256 must be 64 hex characters");
        }
        return sha256.toLowerCase(Locale.ROOT);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidParamException(name + " must not be blank");
        }
    }
}
