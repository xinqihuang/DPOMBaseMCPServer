/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.monitoring.obs.ObsEvidenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OBS 证据转移 MCP 工具测试：委托 service 且不暴露 approve。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class ObsEvidenceToolTest {

    private ObsEvidenceService service;
    private ObsEvidenceTool tool;

    @BeforeEach
    void setUp() {
        service = mock(ObsEvidenceService.class);
        tool = new ObsEvidenceTool(service);
    }

    @Test
    @DisplayName("put 委托 service（传递 base64 字符串）")
    void putDelegatesToService() {
        when(service.putEvidence(eq("svc"), eq("inv"), eq("pkg"), eq("base64-content"), eq("sha256")))
                .thenReturn(new ObsPutEvidenceResponse("key", "etag1", 5));

        Object result = tool.putEvidencePackage("svc", "inv", "pkg", "base64-content", "sha256");

        assertThat(result).isInstanceOf(ObsPutEvidenceResponse.class);
        assertThat(((ObsPutEvidenceResponse) result).etag()).isEqualTo("etag1");
    }

    @Test
    @DisplayName("head 委托 service（含 checksum）")
    void headDelegatesToService() {
        tool.headEvidencePackage("svc", "inv", "pkg", "sha256");

        verify(service).headEvidence("svc", "inv", "pkg", "sha256");
    }

    @Test
    @DisplayName("get 委托 service（含 checksum）")
    void getDelegatesToService() {
        tool.getEvidencePackage("svc", "inv", "pkg", "sha256");

        verify(service).getEvidence("svc", "inv", "pkg", "sha256");
    }
}
