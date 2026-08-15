/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 审批编排测试：字段校验、批准/撤销/原子消费/身份或 checksum 绑定。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class ApprovalServiceTest {

    private static final String SHA = "a".repeat(64);

    private ApprovalService service;

    @BeforeEach
    void setUp() throws IOException {
        ApprovalProperties properties = new ApprovalProperties();
        Path storeFile = Files.createTempFile("dpom-approvals", ".json");
        properties.setStoreFile(storeFile.toString());
        PersistentApprovalStore store = new PersistentApprovalStore(properties);
        service = new ApprovalService(store, properties, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("批准后消费返回记录且 sha256 归一为小写")
    void approveThenConsumeReturnsRecord() {
        service.approve("svc", "inv", "pkg", SHA.toUpperCase(), "approver", "reason");

        ApprovalRecord record = service.consume("svc", "inv", "pkg", SHA);

        assertThat(record.sha256()).isEqualTo(SHA);
        assertThat(record.approverRef()).isEqualTo("approver");
    }

    @Test
    @DisplayName("身份非法时拒绝")
    void invalidIdentityRejected() {
        Throwable throwable = catchThrowable(() ->
                service.approve("svc", "../inv", "pkg", SHA, "approver", "reason"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("sha256 非法时拒绝")
    void invalidChecksumRejected() {
        Throwable throwable = catchThrowable(() ->
                service.approve("svc", "inv", "pkg", "not-a-checksum", "approver", "reason"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("审批人/原因为空时拒绝")
    void blankApproverRejected() {
        Throwable throwable = catchThrowable(() ->
                service.approve("svc", "inv", "pkg", SHA, " ", "reason"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("消费不存在审批抛 UPLOAD_NOT_APPROVED")
    void consumeMissingRejected() {
        Throwable throwable = catchThrowable(() -> service.consume("svc", "inv", "pkg", SHA));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.UPLOAD_NOT_APPROVED);
    }

    @Test
    @DisplayName("撤销不存在审批抛 APPROVAL_NOT_FOUND")
    void revokeMissingRejected() {
        Throwable throwable = catchThrowable(() -> service.revoke("svc", "inv", "pkg", SHA));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.APPROVAL_NOT_FOUND);
    }

    @Test
    @DisplayName("撤销后消费被拒绝")
    void consumeAfterRevokeRejected() {
        service.approve("svc", "inv", "pkg", SHA, "approver", "reason");
        service.revoke("svc", "inv", "pkg", SHA);

        Throwable throwable = catchThrowable(() -> service.consume("svc", "inv", "pkg", SHA));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.UPLOAD_NOT_APPROVED);
    }
}
