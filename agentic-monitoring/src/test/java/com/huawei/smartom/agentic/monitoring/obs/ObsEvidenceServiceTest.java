/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.obs;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalRecord;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OBS 证据转移服务测试：对象名（含 checksum）、审批原子消费、Base64 限长、大小、checksum、证据包校验、回滚与 head/get。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class ObsEvidenceServiceTest {

    private ObsEvidenceAdapter adapter;
    private ObsProperties properties;
    private DiagnosticEvidencePackageValidator packageValidator;
    private ApprovalService approvalService;
    private ObsEvidenceService service;

    @BeforeEach
    void setUp() {
        adapter = mock(ObsEvidenceAdapter.class);
        packageValidator = mock(DiagnosticEvidencePackageValidator.class);
        approvalService = mock(ApprovalService.class);
        properties = new ObsProperties();
        properties.setPrefix("evidence");
        properties.setMaxBytes(1024);
        service = new ObsEvidenceService(adapter, properties, approvalService, packageValidator);
    }

    @Test
    @DisplayName("审批消费后上传成功且对象名为服务端生成（含 checksum）")
    void putEvidenceConsumesApprovalAndGeneratesObjectKey() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        ApprovalRecord record = record("svc", "inv", "pkg", sha256);
        when(approvalService.consume("svc", "inv", "pkg", sha256)).thenReturn(record);
        when(adapter.putEvidence(any(ObsPutEvidenceRequest.class)))
                .thenReturn(new ObsPutEvidenceResponse("evidence/svc/inv/pkg/" + sha256 + ".zip", "etag1", content.length));

        ObsPutEvidenceResponse response = service.putEvidence("svc", "inv", "pkg", base64(content), sha256);

        ArgumentCaptor<ObsPutEvidenceRequest> captor = ArgumentCaptor.forClass(ObsPutEvidenceRequest.class);
        verify(adapter).putEvidence(captor.capture());
        assertThat(captor.getValue().objectKey()).isEqualTo("evidence/svc/inv/pkg/" + sha256 + ".zip");
        assertThat(response.etag()).isEqualTo("etag1");
        verify(approvalService).consume("svc", "inv", "pkg", sha256);
        verify(packageValidator).validate(any(byte[].class), eq("svc"), eq("pkg"));
    }

    @Test
    @DisplayName("未审批上传返回 UPLOAD_NOT_APPROVED")
    void uploadWithoutApprovalRejected() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        when(approvalService.consume("svc", "inv", "pkg", sha256(content)))
                .thenThrow(new SmartomException(ErrorCode.UPLOAD_NOT_APPROVED, "not approved"));

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), sha256(content)));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.UPLOAD_NOT_APPROVED);
        verify(adapter, never()).putEvidence(any(ObsPutEvidenceRequest.class));
    }

    @Test
    @DisplayName("上传失败回滚已消费的审批")
    void uploadFailureRestoresApproval() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        ApprovalRecord record = record("svc", "inv", "pkg", sha256);
        when(approvalService.consume("svc", "inv", "pkg", sha256)).thenReturn(record);
        when(adapter.putEvidence(any(ObsPutEvidenceRequest.class)))
                .thenThrow(new SmartomException(ErrorCode.UPSTREAM_ERROR, "boom"));

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), sha256));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        verify(approvalService).restore(record);
    }

    @Test
    @DisplayName("身份含路径穿越字符时拒绝")
    void pathTraversalRejected() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        Throwable throwable = catchThrowable(() ->
                service.headEvidence("svc", "../inv", "pkg", sha256(content)));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("Base64 解码前先限长")
    void base64TooLongRejected() {
        properties.setMaxBytes(3);
        byte[] content = new byte[15];

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), sha256(content)));

        assertThat(throwable).isInstanceOf(InvalidParamException.class)
                .hasMessageContaining("base64");
    }

    @Test
    @DisplayName("内容超限时拒绝")
    void oversizedContentRejected() {
        properties.setMaxBytes(3);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), sha256(content)));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("checksum 不匹配时拒绝")
    void checksumMismatchRejected() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), "deadbeef"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
        verify(approvalService, never()).consume(any(String.class), any(String.class), any(String.class),
                any(String.class));
    }

    @Test
    @DisplayName("证据包校验失败时拒绝上传且不消费审批")
    void packageValidationRejected() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        doThrow(new InvalidParamException("bad package")).when(packageValidator)
                .validate(any(byte[].class), eq("svc"), eq("pkg"));

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), sha256(content)));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
        verify(approvalService, never()).consume(any(String.class), any(String.class), any(String.class),
                any(String.class));
    }

    @Test
    @DisplayName("head/get 校验 checksum 格式（非 64 hex 拒绝）")
    void invalidChecksumFormatRejected() {
        Throwable throwable = catchThrowable(() ->
                service.headEvidence("svc", "inv", "pkg", "not-a-checksum"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    private ApprovalRecord record(String serviceCode, String investigationId, String packageId, String sha256) {
        long now = System.currentTimeMillis();
        return new ApprovalRecord(serviceCode, investigationId, packageId, sha256, "approver", "reason",
                now + 3600000L, now);
    }

    private String base64(byte[] content) {
        return Base64.getEncoder().encodeToString(content);
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
