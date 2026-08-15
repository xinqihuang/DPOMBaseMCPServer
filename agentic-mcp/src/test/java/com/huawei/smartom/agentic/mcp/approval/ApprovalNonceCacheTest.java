/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.huawei.smartom.agentic.monitoring.approval.ApprovalProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * nonce 防重放缓存测试：重放、过期、格式、容量 fail-closed、重启持久化。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class ApprovalNonceCacheTest {

    private ApprovalProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        properties = new ApprovalProperties();
        Path storeFile = Files.createTempFile("dpom-nonces", ".json");
        properties.setNonceStoreFile(storeFile.toString());
    }

    @Test
    @DisplayName("首次 nonce 接受，同窗口重复 nonce 重放")
    void replayDetected() {
        ApprovalNonceCache cache = new ApprovalNonceCache(properties);
        long validUntil = System.currentTimeMillis() + 60000L;

        assertThat(cache.tryRecord("nonce-000000000001", validUntil)).isTrue();
        assertThat(cache.tryRecord("nonce-000000000001", validUntil)).isFalse();
    }

    @Test
    @DisplayName("已过期的 nonce 可再次接受")
    void expiredNonceAccepted() {
        ApprovalNonceCache cache = new ApprovalNonceCache(properties);
        long future = System.currentTimeMillis() + 60000L;

        assertThat(cache.tryRecord("nonce-000000000002", System.currentTimeMillis() - 1000L)).isTrue();
        assertThat(cache.tryRecord("nonce-000000000002", future)).isTrue();
    }

    @Test
    @DisplayName("非法 nonce 格式/长度拒绝")
    void invalidNonceRejected() {
        ApprovalNonceCache cache = new ApprovalNonceCache(properties);
        long future = System.currentTimeMillis() + 60000L;

        assertThat(cache.tryRecord("short", future)).isFalse();
        assertThat(cache.tryRecord("bad nonce with spaces!", future)).isFalse();
    }

    @Test
    @DisplayName("达到容量且均未过期时 fail-closed 拒绝")
    void fullCapacityFailClosed() {
        properties.setNonceCacheSize(2);
        ApprovalNonceCache cache = new ApprovalNonceCache(properties);
        long future = System.currentTimeMillis() + 60000L;

        assertThat(cache.tryRecord("nonce-000000000010", future)).isTrue();
        assertThat(cache.tryRecord("nonce-000000000011", future)).isTrue();
        assertThat(cache.tryRecord("nonce-000000000012", future)).isFalse();
    }

    @Test
    @DisplayName("重启后窗口内 nonce 仍拒绝重放（持久化）")
    void nonceSurvivesRestart() {
        ApprovalNonceCache first = new ApprovalNonceCache(properties);
        long future = System.currentTimeMillis() + 60000L;
        assertThat(first.tryRecord("nonce-000000000020", future)).isTrue();

        ApprovalNonceCache second = new ApprovalNonceCache(properties);

        assertThat(second.tryRecord("nonce-000000000020", future)).isFalse();
    }
}
