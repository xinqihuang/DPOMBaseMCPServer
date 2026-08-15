/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.obs;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectContent;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectMetadata;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalRecord;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * OBS 证据包转移的业务编排。
 *
 * <p>职责：对象名服务端生成（含内容 checksum）、身份与 checksum 白名单校验、显式 approval 原子消费、
 * Base64 解码前限长、大小与 checksum 校验、证据包结构校验、结构化审计，最终委托 adapter 执行 put/head/get。
 * 审批由可信控制面经 {@link ApprovalService} 触发，不暴露给 MCP；put 成功后审批被原子消费（并发单赢家），
 * 上传失败回滚以便重试。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Service
public class ObsEvidenceService {

    private static final Logger LOG = LoggerFactory.getLogger(ObsEvidenceService.class);

    private final ObsEvidenceAdapter adapter;
    private final ObsProperties properties;
    private final ApprovalService approvalService;
    private final DiagnosticEvidencePackageValidator packageValidator;

    /**
     * 构造 {@code ObsEvidenceService}。
     *
     * @param adapter          OBS 证据转移适配器
     * @param properties       OBS 服务端配置
     * @param approvalService  审批编排（原子消费/回滚）
     * @param packageValidator 证据包校验器
     */
    public ObsEvidenceService(ObsEvidenceAdapter adapter, ObsProperties properties,
            ApprovalService approvalService, DiagnosticEvidencePackageValidator packageValidator) {
        this.adapter = adapter;
        this.properties = properties;
        this.approvalService = approvalService;
        this.packageValidator = packageValidator;
    }

    /**
     * 上传证据包（需显式 approval，原子消费；校验大小、checksum 与证据包结构）。
     *
     * @param serviceCode      服务编码
     * @param investigationId  调查编号
     * @param packageId        证据包编号
     * @param contentBase64    证据包内容的 Base64 编码
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 上传结果
     */
    public ObsPutEvidenceResponse putEvidence(String serviceCode, String investigationId, String packageId,
            String contentBase64, String sha256) {
        return executeAudited("PUT", serviceCode, investigationId, packageId, () -> {
            requireIdentity(serviceCode, investigationId, packageId);
            requireBase64LengthWithinLimit(contentBase64);
            byte[] content = decodeBase64(contentBase64);
            requireSizeWithinLimit(content);
            requireChecksumMatch(content, sha256);
            packageValidator.validate(content, serviceCode, packageId);
            ApprovalRecord approval = approvalService.consume(serviceCode, investigationId, packageId, sha256);
            String objectKey = buildObjectKey(serviceCode, investigationId, packageId, sha256);
            try {
                return adapter.putEvidence(new ObsPutEvidenceRequest(objectKey, content, sha256));
            }
            catch (RuntimeException exception) {
                approvalService.restore(approval);
                throw exception;
            }
        });
    }

    /**
     * 获取证据包对象元数据（head）。
     *
     * @param serviceCode      服务编码
     * @param investigationId  调查编号
     * @param packageId        证据包编号
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 对象元数据
     */
    public ObsObjectMetadata headEvidence(String serviceCode, String investigationId, String packageId, String sha256) {
        return executeAudited("HEAD", serviceCode, investigationId, packageId, () -> {
            requireIdentity(serviceCode, investigationId, packageId);
            validateChecksumFormat(sha256);
            String objectKey = buildObjectKey(serviceCode, investigationId, packageId, sha256);
            return adapter.headEvidence(objectKey);
        });
    }

    /**
     * 获取证据包对象内容（get，限流且受限读取上限）。
     *
     * @param serviceCode      服务编码
     * @param investigationId  调查编号
     * @param packageId        证据包编号
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 对象内容
     */
    public ObsObjectContent getEvidence(String serviceCode, String investigationId, String packageId, String sha256) {
        return executeAudited("GET", serviceCode, investigationId, packageId, () -> {
            requireIdentity(serviceCode, investigationId, packageId);
            validateChecksumFormat(sha256);
            String objectKey = buildObjectKey(serviceCode, investigationId, packageId, sha256);
            return adapter.getEvidence(objectKey);
        });
    }

    private <T> T executeAudited(String eventType, String serviceCode, String investigationId, String packageId,
            Supplier<T> action) {
        try {
            T result = action.get();
            logAudit(eventType, "SUCCESS", null, serviceCode, investigationId, packageId);
            return result;
        }
        catch (SmartomException exception) {
            logAudit(eventType, "FAILURE", exception.getErrorCode().name(), serviceCode, investigationId, packageId);
            throw exception;
        }
        catch (RuntimeException exception) {
            logAudit(eventType, "FAILURE", ErrorCode.INTERNAL.name(), serviceCode, investigationId, packageId);
            throw exception;
        }
    }

    private String buildObjectKey(String serviceCode, String investigationId, String packageId, String sha256) {
        return properties.getPrefix() + "/" + serviceCode + "/" + investigationId + "/"
                + packageId + "/" + sha256 + ".zip";
    }

    private void requireIdentity(String serviceCode, String investigationId, String packageId) {
        validateSegment(serviceCode, "serviceCode");
        validateSegment(investigationId, "investigationId");
        validateSegment(packageId, "packageId");
    }

    private void validateSegment(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]+")) {
            throw new InvalidParamException(name + " must match [a-zA-Z0-9_-]+");
        }
    }

    private void validateChecksumFormat(String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new InvalidParamException("sha256 must be 64 hex characters");
        }
    }

    private void requireBase64LengthWithinLimit(String contentBase64) {
        int maxBase64Length = properties.getMaxBytes() * 4 / 3 + 8;
        if (contentBase64 == null || contentBase64.length() > maxBase64Length) {
            throw new InvalidParamException("content_base64 length exceeds limit");
        }
    }

    private byte[] decodeBase64(String contentBase64) {
        try {
            return Base64.getDecoder().decode(contentBase64);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidParamException("content_base64 is not valid base64");
        }
    }

    private void requireSizeWithinLimit(byte[] content) {
        if (content.length > properties.getMaxBytes()) {
            throw new InvalidParamException(
                    "content size " + content.length + " exceeds limit " + properties.getMaxBytes());
        }
    }

    private void requireChecksumMatch(byte[] content, String sha256) {
        String computed = computeSha256(content);
        if (!computed.equalsIgnoreCase(sha256)) {
            throw new InvalidParamException("sha256 mismatch: expected " + sha256 + ", computed " + computed);
        }
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "SHA-256 算法不可用", null, exception);
        }
    }

    private void logAudit(String eventType, String result, String errorCode,
            String serviceCode, String investigationId, String packageId) {
        LOG.info("OBS evidence audit, eventType={}, result={}, errorCode={}, serviceCode={}, investigationId={}, "
                + "packageId={}", eventType, result, errorCode, sanitizeSegment(serviceCode),
                sanitizeSegment(investigationId), sanitizeSegment(packageId));
    }

    private String sanitizeSegment(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}") ? value : "INVALID";
    }
}
