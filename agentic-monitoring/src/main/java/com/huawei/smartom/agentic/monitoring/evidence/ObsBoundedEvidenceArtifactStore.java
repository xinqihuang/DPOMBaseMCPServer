/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.erdtman.jcs.JsonCanonicalizer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 使用华为云 OBS 固化有界诊断证据的 Artifact store。
 *
 * @author Codex
 * @since 2026-08-26
 */
public final class ObsBoundedEvidenceArtifactStore implements BoundedEvidenceArtifactStore {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern SEGMENT = Pattern.compile("[a-zA-Z0-9_-]{1,128}");
    private static final Pattern PREFIX = Pattern.compile("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*");
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i).*(authorization|cookie|password|secret|token|access.?key|private.?key).*");

    private final ObsEvidenceAdapter adapter;
    private final ObsProperties properties;
    private final ObjectMapper mapper;

    /**
     * 创建 OBS Artifact store，并在启用时校验完整环境配置。
     *
     * @param adapter OBS SDK 适配器
     * @param properties OBS 环境配置
     * @param mapper JSON 映射器
     */
    public ObsBoundedEvidenceArtifactStore(ObsEvidenceAdapter adapter, ObsProperties properties, ObjectMapper mapper) {
        this.adapter = adapter;
        this.properties = properties;
        this.mapper = mapper;
        validateConfiguration();
    }

    /**
     * 将有界响应规范化、摘要并写入环境配置的 OBS 位置。
     *
     * @param investigationId 调查身份
     * @param evidenceType 证据类型
     * @param boundedValue 有界响应
     * @param capturedAt 采集时间
     * @return OBS 引用、摘要和字节数
     */
    @Override
    public StoredEvidence store(String investigationId, String evidenceType, Object boundedValue, Instant capturedAt) {
        validateSegment(investigationId, "investigationId");
        validateSegment(evidenceType, "evidenceType");
        if (boundedValue == null || capturedAt == null) {
            throw new InvalidParamException("boundedValue and capturedAt are required");
        }
        CanonicalEvidence canonical = canonicalize(boundedValue);
        requireWithinLimit(canonical.bytes());
        String objectKey = buildObjectKey(investigationId, evidenceType, canonical.sha256());
        ObsPutEvidenceRequest request = new ObsPutEvidenceRequest(
                objectKey, canonical.bytes(), canonical.sha256(), CONTENT_TYPE_JSON);
        ObsPutEvidenceResponse response = adapter.putEvidence(request);
        verifyResponse(response, objectKey, canonical.bytes().length);
        return new StoredEvidence(buildSourceRef(objectKey), canonical.sha256(), canonical.bytes().length);
    }

    private CanonicalEvidence canonicalize(Object value) {
        try {
            JsonNode tree = mapper.valueToTree(value);
            redactSensitiveFields(tree);
            byte[] bytes = new JsonCanonicalizer(mapper.writeValueAsBytes(tree)).getEncodedUTF8();
            return new CanonicalEvidence(bytes, sha256(bytes));
        }
        catch (SmartomException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new InvalidParamException("bounded evidence cannot be serialized");
        }
        catch (Exception exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "规范化诊断证据失败", null, exception);
        }
    }

    private void redactSensitiveFields(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_FIELD.matcher(field.getKey()).matches()) {
                    object.put(field.getKey(), "[REDACTED]");
                }
                else {
                    redactSensitiveFields(field.getValue());
                }
            }
        }
        else if (node instanceof ArrayNode array) {
            array.forEach(this::redactSensitiveFields);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "SHA-256 算法不可用", null, exception);
        }
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, "自动证据存储要求启用 OBS adapter");
        }
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, "OBS endpoint 未配置");
        }
        if (properties.getBucket() == null || !BUCKET.matcher(properties.getBucket()).matches()) {
            throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, "OBS bucket 配置无效");
        }
        if (properties.getPrefix() == null || !PREFIX.matcher(properties.getPrefix()).matches()) {
            throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, "OBS prefix 配置无效");
        }
        validateSegment(properties.getServiceCode(), "serviceCode");
    }

    private void validateSegment(String value, String name) {
        if (value == null || !SEGMENT.matcher(value).matches()) {
            throw new InvalidParamException(name + " must match [a-zA-Z0-9_-]{1,128}");
        }
    }

    private void requireWithinLimit(byte[] content) {
        if (content.length < 1 || content.length > properties.getMaxBytes()) {
            throw new InvalidParamException("canonical evidence size exceeds configured limit");
        }
    }

    private String buildObjectKey(String investigationId, String evidenceType, String sha256) {
        return properties.getPrefix() + "/" + properties.getServiceCode() + "/" + investigationId + "/"
                + evidenceType + "/" + sha256 + ".json";
    }

    private String buildSourceRef(String objectKey) {
        return "obs://" + properties.getBucket() + "/" + objectKey;
    }

    private void verifyResponse(ObsPutEvidenceResponse response, String objectKey, long size) {
        if (response == null || !objectKey.equals(response.objectKey()) || response.size() != size
                || response.etag() == null || response.etag().isBlank()) {
            throw new SmartomException(ErrorCode.UPSTREAM_ERROR, "OBS 上传结果与请求身份不一致");
        }
    }

    /**
     * 规范化后的证据字节及其摘要。
     *
     * @param bytes UTF-8 规范 JSON
     * @param sha256 SHA-256 十六进制摘要
     *
     * @author Codex
     * @since 2026-08-26
     */
    private record CanonicalEvidence(byte[] bytes, String sha256) {
    }
}
