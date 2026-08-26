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

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.ServerEncryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OBS 证据转移真实实现测试：mock ObsClient，验证 put/head/get 映射与 SSE-KMS、异常映射。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class ObsEvidenceAdapterImplTest {

    private ObsClient obsClient;
    private ObsEvidenceAdapterImpl adapter;
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void setUp() {
        obsClient = mock(ObsClient.class);
        ObsProperties properties = new ObsProperties();
        properties.setBucket("evidence-bucket");
        properties.setKmsKeyId("kms-key-123");
        rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.ofDefaults());
        adapter = new ObsEvidenceAdapterImpl(obsClient, properties, rateLimiterRegistry);
    }

    @Test
    @DisplayName("put 带 SSE-KMS 与 sha256 元数据")
    void putEvidenceAppliesSseKmsAndMetadata() {
        PutObjectResult putResult = mock(PutObjectResult.class);
        when(putResult.getEtag()).thenReturn("etag123");
        when(obsClient.putObject(any(PutObjectRequest.class))).thenReturn(putResult);

        ObsPutEvidenceResponse response = adapter.putEvidence(
                new ObsPutEvidenceRequest("evidence/svc/inv/item.json", new byte[]{1, 2, 3}, "abc123",
                        "application/json"));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(obsClient).putObject(captor.capture());
        PutObjectRequest captured = captor.getValue();
        assertThat(captured.getSseKmsHeader()).isNotNull();
        assertThat(captured.getSseKmsHeader().getEncryption()).isEqualTo(ServerEncryption.OBS_KMS);
        assertThat(captured.getSseKmsHeader().getKmsKeyId()).isEqualTo("kms-key-123");
        assertThat(captured.getMetadata().getContentLength()).isEqualTo(3L);
        assertThat(captured.getMetadata().getContentType()).isEqualTo("application/json");
        assertThat(captured.getMetadata().getUserMetadata("sha256")).isEqualTo("abc123");
        assertThat(response.objectKey()).isEqualTo("evidence/svc/inv/item.json");
        assertThat(response.etag()).isEqualTo("etag123");
        assertThat(response.size()).isEqualTo(3L);
    }

    @Test
    @DisplayName("head 返回对象元数据")
    void headEvidenceReturnsMetadata() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(42L);
        metadata.setEtag("etag-head");
        metadata.addUserMetadata("sha256", "sha-head");
        when(obsClient.getObjectMetadata("evidence-bucket", "key")).thenReturn(metadata);

        ObsObjectMetadata result = adapter.headEvidence("key");

        assertThat(result.objectKey()).isEqualTo("key");
        assertThat(result.contentLength()).isEqualTo(42L);
        assertThat(result.etag()).isEqualTo("etag-head");
        assertThat(result.sha256()).isEqualTo("sha-head");
    }

    @Test
    @DisplayName("get 返回对象内容")
    void getEvidenceReturnsContent() {
        ObsObject obsObject = new ObsObject();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setEtag("etag-get");
        obsObject.setMetadata(metadata);
        InputStream stream = new ByteArrayInputStream(new byte[]{7, 8, 9});
        obsObject.setObjectContent(stream);
        when(obsClient.getObject("evidence-bucket", "key")).thenReturn(obsObject);

        ObsObjectContent result = adapter.getEvidence("key");

        assertThat(result.objectKey()).isEqualTo("key");
        assertThat(result.content()).containsExactly(7, 8, 9);
        assertThat(result.etag()).isEqualTo("etag-get");
    }

    @Test
    @DisplayName("get 读取超过最大上限时拒绝")
    void getExceedsReadLimitRejected() {
        ObsProperties smallProperties = new ObsProperties();
        smallProperties.setBucket("evidence-bucket");
        smallProperties.setKmsKeyId("kms-key-123");
        smallProperties.setMaxBytes(10);
        ObsEvidenceAdapterImpl smallAdapter = new ObsEvidenceAdapterImpl(obsClient, smallProperties, rateLimiterRegistry);
        ObsObject obsObject = new ObsObject();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(999L);
        obsObject.setMetadata(metadata);
        when(obsClient.getObject("evidence-bucket", "key")).thenReturn(obsObject);

        Throwable throwable = catchThrowable(() -> smallAdapter.getEvidence("key"));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM);
    }

    @Test
    @DisplayName("get 元数据缺失 contentLength 时按实际读取量拒绝超限内容")
    void getRejectsWhenContentLengthMissing() {
        ObsProperties smallProperties = new ObsProperties();
        smallProperties.setBucket("evidence-bucket");
        smallProperties.setKmsKeyId("kms-key-123");
        smallProperties.setMaxBytes(10);
        ObsEvidenceAdapterImpl smallAdapter = new ObsEvidenceAdapterImpl(obsClient, smallProperties, rateLimiterRegistry);
        ObsObject obsObject = new ObsObject();
        obsObject.setMetadata(new ObjectMetadata());
        obsObject.setObjectContent(new ByteArrayInputStream(new byte[15]));
        when(obsClient.getObject("evidence-bucket", "key")).thenReturn(obsObject);

        Throwable throwable = catchThrowable(() -> smallAdapter.getEvidence("key"));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM);
    }

    @Test
    @DisplayName("get 元数据 contentLength 少报时按实际读取量拒绝超限内容")
    void getRejectsWhenContentLengthUnderreports() {
        ObsProperties smallProperties = new ObsProperties();
        smallProperties.setBucket("evidence-bucket");
        smallProperties.setKmsKeyId("kms-key-123");
        smallProperties.setMaxBytes(10);
        ObsEvidenceAdapterImpl smallAdapter = new ObsEvidenceAdapterImpl(obsClient, smallProperties, rateLimiterRegistry);
        ObsObject obsObject = new ObsObject();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(5L);
        obsObject.setMetadata(metadata);
        obsObject.setObjectContent(new ByteArrayInputStream(new byte[15]));
        when(obsClient.getObject("evidence-bucket", "key")).thenReturn(obsObject);

        Throwable throwable = catchThrowable(() -> smallAdapter.getEvidence("key"));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM);
    }

    @Test
    @DisplayName("OBS 429 异常映射为 UPSTREAM_THROTTLED")
    void obsExceptionMappedToThrottled() {
        ObsException obsException = mock(ObsException.class);
        when(obsException.getResponseCode()).thenReturn(429);
        when(obsException.getErrorRequestId()).thenReturn("request-id-1");
        when(obsException.getErrorMessage()).thenReturn("rate limited");
        when(obsClient.putObject(any(PutObjectRequest.class))).thenThrow(obsException);

        Throwable throwable = catchThrowable(() -> adapter.putEvidence(
                new ObsPutEvidenceRequest("key", new byte[]{1}, "abc")));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED);
        assertThat(((SmartomException) throwable).getUpstreamTraceId()).isEqualTo("request-id-1");
    }

    @Test
    @DisplayName("未配置 KMS key 时仍使用默认 SSE-KMS 主密钥")
    void missingKmsKeyUsesDefaultKmsKey() {
        ObsProperties properties = new ObsProperties();
        properties.setBucket("evidence-bucket");
        properties.setKmsKeyId("");
        ObsEvidenceAdapterImpl noKmsAdapter = new ObsEvidenceAdapterImpl(obsClient, properties, rateLimiterRegistry);
        PutObjectResult putResult = mock(PutObjectResult.class);
        when(putResult.getEtag()).thenReturn("etag-default-kms");
        when(obsClient.putObject(any(PutObjectRequest.class))).thenReturn(putResult);

        noKmsAdapter.putEvidence(new ObsPutEvidenceRequest("key", new byte[]{1}, "abc", "application/json"));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(obsClient).putObject(captor.capture());
        assertThat(captor.getValue().getSseKmsHeader().getEncryption()).isEqualTo(ServerEncryption.OBS_KMS);
        assertThat(captor.getValue().getSseKmsHeader().getKmsKeyId()).isNull();
    }
}
