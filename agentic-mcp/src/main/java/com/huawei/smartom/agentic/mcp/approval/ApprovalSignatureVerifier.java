/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalProperties;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 审批控制面 HMAC 签名校验：timestamp + nonce + method + path + body hash，恒时比较、防重放。
 *
 * <p>支持主密钥 + 上一代密钥平滑轮换；主密钥强度低于 32 字符时 fail-closed；nonce 有效终点覆盖签名实际有效窗口
 * （timestamp + tolerance）而非 now + tolerance；认证失败统一 401 稳定消息，不区分失败原因。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Component
@ConditionalOnProperty(name = "dpom.approval.enabled", havingValue = "true")
public class ApprovalSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 32;

    private final ApprovalProperties properties;
    private final ApprovalNonceCache nonceCache;
    private final MeterRegistry meterRegistry;

    /**
     * 构造签名校验器。
     *
     * @param properties     审批配置（hmac-secret / hmac-previous-secret / timestamp-tolerance-seconds）
     * @param nonceCache     nonce 防重放缓存
     * @param meterRegistry  Micrometer 指标注册表
     */
    public ApprovalSignatureVerifier(ApprovalProperties properties, ApprovalNonceCache nonceCache,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.nonceCache = nonceCache;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 校验请求签名；失败统一抛 {@code APPROVAL_AUTH_FAILED}（稳定消息）。
     *
     * @param method            HTTP 方法
     * @param path              请求路径（含路径变量实际值）
     * @param body              请求体原始字节
     * @param timestampHeader   X-Approval-Timestamp 头
     * @param nonceHeader       X-Approval-Nonce 头
     * @param signatureHeader   X-Approval-Signature 头
     */
    public void verify(String method, String path, byte[] body, String timestampHeader, String nonceHeader,
            String signatureHeader) {
        requireSecrets();
        long timestamp = parseTimestamp(timestampHeader);
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSeconds - timestamp) > properties.getTimestampToleranceSeconds()) {
            throw authFailure();
        }
        if (nonceHeader == null || nonceHeader.isBlank()) {
            throw authFailure();
        }
        String canonical = timestampHeader + "\n" + nonceHeader + "\n" + method + "\n" + path + "\n"
                + sha256Hex(body);
        byte[] provided = parseHex(signatureHeader);
        boolean matched = MessageDigest.isEqual(hmac(properties.getHmacSecret(), canonical), provided);
        if (!matched && hasPreviousSecret()) {
            matched = MessageDigest.isEqual(hmac(properties.getHmacPreviousSecret(), canonical), provided);
        }
        if (!matched) {
            throw authFailure();
        }
        long nonceValidUntil = (timestamp + properties.getTimestampToleranceSeconds()) * 1000L;
        if (!nonceCache.tryRecord(nonceHeader, nonceValidUntil)) {
            throw authFailure();
        }
    }

    private void requireSecrets() {
        if (properties.getHmacSecret() == null || properties.getHmacSecret().length() < MIN_SECRET_LENGTH) {
            throw authFailure();
        }
        String previous = properties.getHmacPreviousSecret();
        if (previous != null && !previous.isBlank() && previous.length() < MIN_SECRET_LENGTH) {
            throw authFailure();
        }
    }

    private boolean hasPreviousSecret() {
        String previous = properties.getHmacPreviousSecret();
        return previous != null && !previous.isBlank();
    }

    private long parseTimestamp(String timestampHeader) {
        try {
            return Long.parseLong(timestampHeader);
        }
        catch (NumberFormatException exception) {
            throw authFailure();
        }
    }

    private byte[] parseHex(String signatureHeader) {
        if (signatureHeader == null) {
            throw authFailure();
        }
        try {
            return HexFormat.of().parseHex(signatureHeader);
        }
        catch (IllegalArgumentException exception) {
            throw authFailure();
        }
    }

    private byte[] hmac(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        }
        catch (GeneralSecurityException exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "HMAC computation failed");
        }
    }

    private String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "SHA-256 unavailable");
        }
    }

    private SmartomException authFailure() {
        meterRegistry.counter("dpom.approval.auth.failure").increment();
        return new SmartomException(ErrorCode.APPROVAL_AUTH_FAILED, "authentication failed");
    }
}
