/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 持久化审批存储测试：批准/撤销/原子消费/过期/重启持久化/并发单赢家/持久化失败回滚/损坏 fail-closed。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class PersistentApprovalStoreTest {

    private static final String SHA = "a".repeat(64);

    private ApprovalProperties properties;
    private Path storeFile;

    @BeforeEach
    void setUp() throws IOException {
        properties = new ApprovalProperties();
        storeFile = Files.createTempFile("dpom-approvals", ".json");
        properties.setStoreFile(storeFile.toString());
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(storeFile);
    }

    @Test
    @DisplayName("批准后可查询到未过期审批")
    void approveThenIsApproved() {
        PersistentApprovalStore store = new PersistentApprovalStore(properties);
        store.approve(record(System.currentTimeMillis() + 3600000L));

        assertThat(store.isApproved("svc", "inv", "pkg", SHA)).isTrue();
    }

    @Test
    @DisplayName("撤销后不再批准")
    void revokeRemovesApproval() {
        PersistentApprovalStore store = new PersistentApprovalStore(properties);
        store.approve(record(System.currentTimeMillis() + 3600000L));

        assertThat(store.revoke("svc", "inv", "pkg", SHA)).isTrue();
        assertThat(store.isApproved("svc", "inv", "pkg", SHA)).isFalse();
    }

    @Test
    @DisplayName("撤销不存在的审批返回 false")
    void revokeMissingReturnsFalse() {
        PersistentApprovalStore store = new PersistentApprovalStore(properties);

        assertThat(store.revoke("svc", "inv", "pkg", SHA)).isFalse();
    }

    @Test
    @DisplayName("原子消费：第一次返回记录，第二次返回 null")
    void consumeOnceThenNull() {
        PersistentApprovalStore store = new PersistentApprovalStore(properties);
        store.approve(record(System.currentTimeMillis() + 3600000L));

        assertThat(store.consume("svc", "inv", "pkg", SHA)).isNotNull();
        assertThat(store.consume("svc", "inv", "pkg", SHA)).isNull();
    }

    @Test
    @DisplayName("过期审批消费返回 null 并清理")
    void expiredConsumeReturnsNull() {
        PersistentApprovalStore store = new PersistentApprovalStore(properties);
        store.approve(record(System.currentTimeMillis() - 1000L));

        assertThat(store.consume("svc", "inv", "pkg", SHA)).isNull();
        assertThat(store.isApproved("svc", "inv", "pkg", SHA)).isFalse();
    }

    @Test
    @DisplayName("重启后审批仍在（文件持久化）")
    void approvalSurvivesRestart() {
        PersistentApprovalStore first = new PersistentApprovalStore(properties);
        first.approve(record(System.currentTimeMillis() + 3600000L));

        PersistentApprovalStore second = new PersistentApprovalStore(properties);

        assertThat(second.isApproved("svc", "inv", "pkg", SHA)).isTrue();
    }

    @Test
    @DisplayName("并发消费仅一个赢家")
    void concurrentConsumeSingleWinner() {
        PersistentApprovalStore store = new PersistentApprovalStore(properties);
        store.approve(record(System.currentTimeMillis() + 3600000L));
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                awaitQuietly(start);
                return store.consume("svc", "inv", "pkg", SHA) != null;
            }));
        }
        start.countDown();
        long wins = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        assertThat(wins).isEqualTo(1);
    }

    @Test
    @DisplayName("持久化失败时回滚并抛 APPROVAL_STORAGE_ERROR")
    void diskFailureRollsBackApproval() throws IOException {
        Path parentFile = Files.createTempFile("not-a-dir", ".txt");
        properties.setStoreFile(parentFile + "/approvals.json");
        PersistentApprovalStore store = new PersistentApprovalStore(properties);

        Throwable throwable = catchThrowable(() -> store.approve(record(System.currentTimeMillis() + 3600000L)));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.APPROVAL_STORAGE_ERROR);
        assertThat(store.isApproved("svc", "inv", "pkg", SHA)).isFalse();
    }

    @Test
    @DisplayName("启动时存储文件损坏 fail-closed")
    void corruptedStoreFileFailsClosed() throws IOException {
        Files.writeString(storeFile, "not-json{{{");

        Throwable throwable = catchThrowable(() -> new PersistentApprovalStore(properties));

        assertThat(throwable).isInstanceOf(IllegalStateException.class);
    }

    private ApprovalRecord record(long expiresAtMillis) {
        long now = System.currentTimeMillis();
        return new ApprovalRecord("svc", "inv", "pkg", SHA, "approver", "reason", expiresAtMillis, now);
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
