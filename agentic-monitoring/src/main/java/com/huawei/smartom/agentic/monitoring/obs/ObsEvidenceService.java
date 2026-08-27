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
 * <p>职责：对象名服务端生成（含内容 checksum）、身份与 checksum 白名单校验、Base64 解码前限长、
 * 大小与 checksum 校验、证据包结构校验、结构化审计，最终委托 adapter 执行 put/head/get。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Service
public class ObsEvidenceService {

    private static final Logger LOG = LoggerFactory.getLogger(ObsEvidenceService.class);

    private final ObsEvidenceAdapter adapter;
    private final ObsProperties properties;
    private final DiagnosticEvidencePackageValidator packageValidator;

    /**
     * 构造 {@code ObsEvidenceService}。
     *
     * @param adapter          OBS 证据转移适配器
     * @param properties       OBS 服务端配置
     * @param packageValidator 证据包校验器
     */
    public ObsEvidenceService(ObsEvidenceAdapter adapter, ObsProperties properties,
            DiagnosticEvidencePackageValidator packageValidator) {
        this.adapter = adapter;
        this.properties = properties;
        this.packageValidator = packageValidator;
    }

    /**
     * 上传证据包（校验大小、checksum 与证据包结构，无逐包人工审批）。
     *
     * @param serviceCode      服务编码
     * @param collectionId  调用方提供的证据集合编号
     * @param packageId        证据包编号
     * @param contentBase64    证据包内容的 Base64 编码
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 上传结果
     */
    public ObsPutEvidenceResponse putEvidence(String serviceCode, String collectionId, String packageId,
            String contentBase64, String sha256) {
        return executeAudited("PUT", serviceCode, collectionId, packageId, () -> {
            requireIdentity(serviceCode, collectionId, packageId);
            requireBase64LengthWithinLimit(contentBase64);
            byte[] content = decodeBase64(contentBase64);
            requireSizeWithinLimit(content);
            requireChecksumMatch(content, sha256);
            packageValidator.validate(content, serviceCode, packageId);
            String objectKey = buildObjectKey(serviceCode, collectionId, packageId, sha256);
            return adapter.putEvidence(new ObsPutEvidenceRequest(
                    objectKey, content, sha256, "application/zip"));
        });
    }

    /**
     * 获取证据包对象元数据（head）。
     *
     * @param serviceCode      服务编码
     * @param collectionId  调用方提供的证据集合编号
     * @param packageId        证据包编号
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 对象元数据
     */
    public ObsObjectMetadata headEvidence(String serviceCode, String collectionId, String packageId, String sha256) {
        return executeAudited("HEAD", serviceCode, collectionId, packageId, () -> {
            requireIdentity(serviceCode, collectionId, packageId);
            validateChecksumFormat(sha256);
            String objectKey = buildObjectKey(serviceCode, collectionId, packageId, sha256);
            return adapter.headEvidence(objectKey);
        });
    }

    /**
     * 获取证据包对象内容（get，限流且受限读取上限）。
     *
     * @param serviceCode      服务编码
     * @param collectionId  调用方提供的证据集合编号
     * @param packageId        证据包编号
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 对象内容
     */
    public ObsObjectContent getEvidence(String serviceCode, String collectionId, String packageId, String sha256) {
        return executeAudited("GET", serviceCode, collectionId, packageId, () -> {
            requireIdentity(serviceCode, collectionId, packageId);
            validateChecksumFormat(sha256);
            String objectKey = buildObjectKey(serviceCode, collectionId, packageId, sha256);
            return adapter.getEvidence(objectKey);
        });
    }

    private <T> T executeAudited(String eventType, String serviceCode, String collectionId, String packageId,
            Supplier<T> action) {
        try {
            T result = action.get();
            logAudit(eventType, "SUCCESS", null, serviceCode, collectionId, packageId);
            return result;
        }
        catch (SmartomException exception) {
            logAudit(eventType, "FAILURE", exception.getErrorCode().name(), serviceCode, collectionId, packageId);
            throw exception;
        }
        catch (RuntimeException exception) {
            logAudit(eventType, "FAILURE", ErrorCode.INTERNAL.name(), serviceCode, collectionId, packageId);
            throw exception;
        }
    }

    private String buildObjectKey(String serviceCode, String collectionId, String packageId, String sha256) {
        return properties.getPrefix() + "/" + serviceCode + "/" + collectionId + "/"
                + packageId + "/" + sha256 + ".zip";
    }

    private void requireIdentity(String serviceCode, String collectionId, String packageId) {
        validateSegment(serviceCode, "serviceCode");
        validateSegment(collectionId, "collectionId");
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
            String serviceCode, String collectionId, String packageId) {
        LOG.info("OBS evidence audit, eventType={}, result={}, errorCode={}, serviceCode={}, collectionId={}, "
                + "packageId={}", eventType, result, errorCode, sanitizeSegment(serviceCode),
                sanitizeSegment(collectionId), sanitizeSegment(packageId));
    }

    private String sanitizeSegment(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}") ? value : "INVALID";
    }
}
