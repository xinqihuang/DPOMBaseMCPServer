/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs;

import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectContent;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectMetadata;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.ServerEncryption;
import com.obs.services.model.SseKmsHeader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 基于华为云 OBS SDK（esdk-obs-java 3.26.6）的 {@link ObsEvidenceAdapter} 默认实现。
 *
 * <p>仅承担内部 DTO 与 OBS SDK 请求/响应之间的映射，以及 SSE-KMS 服务端加密头构造。
 * OBS SDK 异常在本类本地映射为 {@code UpstreamException}，不透传到上层。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Component
@ConditionalOnProperty(name = "dpom.obs.enabled", havingValue = "true")
public class ObsEvidenceAdapterImpl implements ObsEvidenceAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ObsEvidenceAdapterImpl.class);

    private static final String USER_METADATA_SHA256 = "sha256";

    private final ObsClient obsClient;
    private final ObsProperties properties;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * 构造 {@code ObsEvidenceAdapterImpl}，注入 OBS 客户端、服务端配置与限流注册表。
     *
     * @param obsClient          已配置的 OBS 客户端
     * @param properties         OBS 服务端配置（bucket / prefix / endpoint / kms-key-id）
     * @param rateLimiterRegistry 用于 OBS 读取限流的注册表
     */
    public ObsEvidenceAdapterImpl(ObsClient obsClient, ObsProperties properties,
            RateLimiterRegistry rateLimiterRegistry) {
        this.obsClient = obsClient;
        this.properties = properties;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public ObsPutEvidenceResponse putEvidence(ObsPutEvidenceRequest request) {
        PutObjectRequest sdkRequest = toPutObjectRequest(request);
        try {
            PutObjectResult result = obsClient.putObject(sdkRequest);
            LOG.info("OBS putEvidence success, objectKey={}, size={}", request.objectKey(), request.content().length);
            return new ObsPutEvidenceResponse(request.objectKey(), result.getEtag(), request.content().length);
        }
        catch (ObsException exception) {
            throw mapObsException(exception);
        }
    }

    @Override
    public ObsObjectMetadata headEvidence(String objectKey) {
        try {
            ObjectMetadata metadata = obsClient.getObjectMetadata(properties.getBucket(), objectKey);
            long length = metadata.getContentLength() == null ? 0L : metadata.getContentLength();
            Object sha256Metadata = metadata.getUserMetadata(USER_METADATA_SHA256);
            String sha256 = sha256Metadata == null ? null : sha256Metadata.toString();
            return new ObsObjectMetadata(objectKey, length, metadata.getEtag(), sha256);
        }
        catch (ObsException exception) {
            throw mapObsException(exception);
        }
    }

    @Override
    public ObsObjectContent getEvidence(String objectKey) {
        try {
            return rateLimiterRegistry.rateLimiter("obs-transfer")
                    .executeSupplier(() -> doGetEvidence(objectKey));
        }
        catch (RequestNotPermitted exception) {
            throw new UpstreamException(ErrorCode.UPSTREAM_THROTTLED, "OBS 读取限流", null, exception);
        }
    }

    private ObsObjectContent doGetEvidence(String objectKey) {
        try {
            ObsObject obsObject = obsClient.getObject(properties.getBucket(), objectKey);
            ObjectMetadata metadata = obsObject.getMetadata();
            long contentLength = metadata == null || metadata.getContentLength() == null
                    ? -1L : metadata.getContentLength();
            if (contentLength > properties.getMaxBytes()) {
                throw new SmartomException(ErrorCode.INVALID_PARAM, "object exceeds max read limit");
            }
            byte[] content;
            try (InputStream input = obsObject.getObjectContent()) {
                content = readBounded(input, properties.getMaxBytes());
            }
            String etag = metadata == null ? null : metadata.getEtag();
            return new ObsObjectContent(objectKey, content, etag);
        }
        catch (ObsException exception) {
            throw mapObsException(exception);
        }
        catch (IOException exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "读取 OBS 对象内容失败", null, exception);
        }
    }

    private PutObjectRequest toPutObjectRequest(ObsPutEvidenceRequest request) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength((long) request.content().length);
        metadata.setContentType(request.contentType());
        metadata.addUserMetadata(USER_METADATA_SHA256, request.sha256());

        SseKmsHeader sseKmsHeader = new SseKmsHeader();
        sseKmsHeader.setEncryption(ServerEncryption.OBS_KMS);
        if (properties.getKmsKeyId() != null && !properties.getKmsKeyId().isBlank()) {
            sseKmsHeader.setKmsKeyId(properties.getKmsKeyId());
        }

        PutObjectRequest sdkRequest = new PutObjectRequest(properties.getBucket(), request.objectKey(),
                new ByteArrayInputStream(request.content()));
        sdkRequest.setMetadata(metadata);
        sdkRequest.setSseKmsHeader(sseKmsHeader);
        return sdkRequest;
    }

    private byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        byte[] buffer = input.readNBytes(maxBytes + 1);
        if (buffer.length > maxBytes) {
            throw new SmartomException(ErrorCode.INVALID_PARAM, "object exceeds max read limit");
        }
        return buffer;
    }

    private SmartomException mapObsException(ObsException exception) {
        ErrorCode errorCode = classifyObsStatus(exception.getResponseCode());
        String message = exception.getErrorMessage() == null
                ? exception.getClass().getSimpleName() : exception.getErrorMessage();
        return new UpstreamException(errorCode, message, exception.getErrorRequestId(), exception);
    }

    private ErrorCode classifyObsStatus(int status) {
        if (status == 429) {
            return ErrorCode.UPSTREAM_THROTTLED;
        }
        if (status == 401 || status == 403) {
            return ErrorCode.UPSTREAM_AUTH_FAILED;
        }
        return ErrorCode.UPSTREAM_ERROR;
    }

}
