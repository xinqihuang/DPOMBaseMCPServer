/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.obs;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

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
 * OBS 证据转移服务无人工审批测试。
 *
 * @author Codex
 * @since 2026-08-26
 */
class ObsEvidenceServiceTest {

    private ObsEvidenceAdapter adapter;
    private ObsProperties properties;
    private DiagnosticEvidencePackageValidator packageValidator;
    private ObsEvidenceService service;

    @BeforeEach
    void setUp() {
        adapter = mock(ObsEvidenceAdapter.class);
        packageValidator = mock(DiagnosticEvidencePackageValidator.class);
        properties = new ObsProperties();
        properties.setPrefix("evidence");
        properties.setMaxBytes(1024);
        service = new ObsEvidenceService(adapter, properties, packageValidator);
    }

    @Test
    @DisplayName("有效证据包无需人工审批即可上传")
    void putEvidenceUploadsWithoutApproval() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        when(adapter.putEvidence(any(ObsPutEvidenceRequest.class)))
                .thenReturn(new ObsPutEvidenceResponse("evidence/svc/inv/pkg/" + sha256 + ".zip",
                        "etag1", content.length));

        ObsPutEvidenceResponse response = service.putEvidence("svc", "inv", "pkg", base64(content), sha256);

        ArgumentCaptor<ObsPutEvidenceRequest> captor = ArgumentCaptor.forClass(ObsPutEvidenceRequest.class);
        verify(adapter).putEvidence(captor.capture());
        assertThat(captor.getValue().objectKey()).isEqualTo("evidence/svc/inv/pkg/" + sha256 + ".zip");
        assertThat(captor.getValue().contentType()).isEqualTo("application/zip");
        assertThat(response.etag()).isEqualTo("etag1");
        verify(packageValidator).validate(any(byte[].class), eq("svc"), eq("pkg"));
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

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("base64");
        verify(adapter, never()).putEvidence(any(ObsPutEvidenceRequest.class));
    }

    @Test
    @DisplayName("checksum 不匹配时拒绝")
    void checksumMismatchRejected() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), "deadbeef"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).putEvidence(any(ObsPutEvidenceRequest.class));
    }

    @Test
    @DisplayName("证据包校验失败时拒绝上传")
    void packageValidationRejected() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        doThrow(new InvalidParamException("bad package")).when(packageValidator)
                .validate(any(byte[].class), eq("svc"), eq("pkg"));

        Throwable throwable = catchThrowable(() ->
                service.putEvidence("svc", "inv", "pkg", base64(content), sha256(content)));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
        verify(adapter, never()).putEvidence(any(ObsPutEvidenceRequest.class));
    }

    @Test
    @DisplayName("head/get 校验 checksum 格式")
    void invalidChecksumFormatRejected() {
        Throwable throwable = catchThrowable(() ->
                service.headEvidence("svc", "inv", "pkg", "not-a-checksum"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    private String base64(byte[] content) {
        return Base64.getEncoder().encodeToString(content);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
