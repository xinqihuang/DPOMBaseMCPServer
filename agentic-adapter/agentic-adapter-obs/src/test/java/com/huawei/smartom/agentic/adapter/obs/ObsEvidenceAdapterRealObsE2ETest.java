/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs;

import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectContent;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectMetadata;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import com.obs.services.ObsClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用环境变量指定目标的真实 OBS 上传、HEAD 和 GET 验证。
 *
 * @author Codex
 * @since 2026-08-26
 */
class ObsEvidenceAdapterRealObsE2ETest {

    @Test
    @DisplayName("真实 OBS 上传后可通过 HEAD 和 GET 校验")
    @EnabledIfEnvironmentVariable(named = "RUN_OBS_E2E", matches = "true")
    void uploadsAndReadsBackConfiguredVerificationObject() throws Exception {
        String endpoint = requiredEnvironment("DPOM_OBS_ENDPOINT");
        String bucket = requiredEnvironment("DPOM_OBS_BUCKET");
        String prefix = requiredEnvironment("DPOM_OBS_PREFIX");
        byte[] content = verificationContent();
        String sha256 = sha256(content);
        String objectKey = prefix + "/verification/" + Instant.now().toEpochMilli() + "-" + sha256 + ".json";
        ObsProperties properties = properties(endpoint, bucket);

        ObsClient client = new ObsClient(requiredEnvironment("HUAWEICLOUD_AK"),
                requiredEnvironment("HUAWEICLOUD_SK"), endpoint);
        try {
            ObsEvidenceAdapterImpl adapter = new ObsEvidenceAdapterImpl(client, properties,
                    RateLimiterRegistry.of(RateLimiterConfig.ofDefaults()));
            ObsPutEvidenceResponse put = adapter.putEvidence(
                    new ObsPutEvidenceRequest(objectKey, content, sha256, "application/json"));
            ObsObjectMetadata head = adapter.headEvidence(objectKey);
            ObsObjectContent get = adapter.getEvidence(objectKey);

            assertThat(put.objectKey()).isEqualTo(objectKey);
            assertThat(put.etag()).isNotBlank();
            assertThat(head.contentLength()).isEqualTo(content.length);
            assertThat(head.etag()).isNotBlank();
            assertThat(head.sha256()).isEqualTo(sha256);
            assertThat(get.content()).containsExactly(content);
            assertThat(get.etag()).isNotBlank();
            System.out.println("OBS_E2E_OBJECT_REF=obs://" + bucket + "/" + objectKey);
        }
        finally {
            client.close();
        }
    }

    private ObsProperties properties(String endpoint, String bucket) {
        ObsProperties properties = new ObsProperties();
        properties.setEndpoint(endpoint);
        properties.setBucket(bucket);
        properties.setKmsKeyId(System.getenv("DPOM_OBS_KMS_KEY_ID"));
        properties.setMaxBytes(4096);
        return properties;
    }

    private byte[] verificationContent() {
        String content = "{\"kind\":\"dpom-obs-verification\",\"schemaVersion\":\"1.0\"}";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_OBS_E2E=true");
        }
        return value;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
