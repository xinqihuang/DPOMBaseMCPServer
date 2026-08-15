/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HMAC 签名校验测试：伪造/篡改/过期/重放/密钥强度/轮换/密钥窗口。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class ApprovalSignatureVerifierTest {

    private static final String PRIMARY = "primary-secret-key-0123456789abcdef";
    private static final String PREVIOUS = "previous-secret-key-0123456789abcdef";
    private static final String METHOD = "POST";
    private static final String PATH = "/internal/approvals";

    private ApprovalProperties properties;
    private ApprovalSignatureVerifier verifier;

    @BeforeEach
    void setUp() throws IOException {
        properties = new ApprovalProperties();
        properties.setHmacSecret(PRIMARY);
        properties.setTimestampToleranceSeconds(300);
        properties.setNonceStoreFile(Files.createTempFile("dpom-nonces", ".json").toString());
        verifier = new ApprovalSignatureVerifier(properties, new ApprovalNonceCache(properties),
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("合法签名通过")
    void validSignaturePasses() {
        byte[] body = "{\"serviceCode\":\"svc\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000101";
        String signature = sign(PRIMARY, timestamp, nonce, body);

        assertThatCode(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("上一代密钥签名仍被接受（平滑轮换）")
    void previousKeyAccepted() {
        properties.setHmacPreviousSecret(PREVIOUS);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000102";
        String signature = sign(PREVIOUS, timestamp, nonce, body);

        assertThatCode(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("移除旧密钥后旧密钥签名拒绝")
    void oldKeyRejectedAfterRotation() {
        properties.setHmacPreviousSecret("");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000103";
        String signature = sign(PREVIOUS, timestamp, nonce, body);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature)))
                .isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("错误密钥签名拒绝")
    void wrongSecretRejected() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000104";
        String signature = sign("other-secret-key-0123456789abcdef", timestamp, nonce, body);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature)))
                .isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("篡改 body 签名拒绝")
    void tamperedBodyRejected() {
        byte[] signedBody = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000105";
        String signature = sign(PRIMARY, timestamp, nonce, signedBody);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, "{\"b\":2}".getBytes(StandardCharsets.UTF_8),
                timestamp, nonce, signature))).isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("过期时间戳拒绝")
    void expiredTimestampRejected() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L - 3600L);
        String nonce = "nonce-000000000106";
        String signature = sign(PRIMARY, timestamp, nonce, body);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature)))
                .isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("重放 nonce 拒绝")
    void replayedNonceRejected() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000107";
        String signature = sign(PRIMARY, timestamp, nonce, body);
        verifier.verify(METHOD, PATH, body, timestamp, nonce, signature);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature)))
                .isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("密钥为空 fail-closed")
    void blankSecretRejected() {
        properties.setHmacSecret("");

        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000108";
        String signature = sign(PRIMARY, timestamp, nonce, body);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature)))
                .isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("密钥强度不足（< 32 字符）fail-closed")
    void weakSecretRejected() {
        properties.setHmacSecret("short-secret");

        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = "nonce-000000000109";
        String signature = sign("short-secret", timestamp, nonce, body);

        assertThat(authError(() -> verifier.verify(METHOD, PATH, body, timestamp, nonce, signature)))
                .isEqualTo(ErrorCode.APPROVAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("nonce 有效终点覆盖签名实际窗口（timestamp+tolerance）")
    void nonceValidUntilCoversSignatureWindow() {
        ApprovalNonceCache nonceCache = mock(ApprovalNonceCache.class);
        when(nonceCache.tryRecord(anyString(), anyLong())).thenReturn(true);
        ApprovalSignatureVerifier target = new ApprovalSignatureVerifier(properties, nonceCache,
                new SimpleMeterRegistry());
        long timestamp = System.currentTimeMillis() / 1000L + 100L;
        String timestampHeader = String.valueOf(timestamp);
        String nonce = "nonce-000000000110";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(PRIMARY, timestampHeader, nonce, body);

        target.verify(METHOD, PATH, body, timestampHeader, nonce, signature);

        long expected = (timestamp + properties.getTimestampToleranceSeconds()) * 1000L;
        verify(nonceCache).tryRecord(eq(nonce), eq(expected));
    }

    private ErrorCode authError(Runnable action) {
        Throwable throwable = catchThrowable(action::run);
        assertThat(throwable).isInstanceOf(SmartomException.class);
        return ((SmartomException) throwable).getErrorCode();
    }

    private String sign(String secret, String timestamp, String nonce, byte[] body) {
        try {
            String canonical = timestamp + "\n" + nonce + "\n" + METHOD + "\n" + PATH + "\n" + sha256Hex(body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
