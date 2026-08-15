/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.mcp.tool.McpTool;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalProperties;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalRecord;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批控制面控制器测试：批准/撤销、字段白名单、body 限长、客户端凭据拒绝、认证失败统一 401 稳定消息、
 * 未分类异常 500 稳定消息、非 MCP 工具。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class ApprovalControlPlaneControllerTest {

    private static final String SHA = "a".repeat(64);

    private ApprovalService approvalService;
    private ApprovalSignatureVerifier verifier;
    private ApprovalProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        verifier = mock(ApprovalSignatureVerifier.class);
        properties = new ApprovalProperties();
        properties.setMaxBodyBytes(4096);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ApprovalControlPlaneController(approvalService, verifier, properties)).build();
    }

    @Test
    @DisplayName("批准返回 201 并回显身份")
    void approveReturnsCreated() throws Exception {
        long now = System.currentTimeMillis();
        when(approvalService.approve("svc", "inv", "pkg", SHA, "approver", "reason"))
                .thenReturn(new ApprovalRecord("svc", "inv", "pkg", SHA, "approver", "reason", now + 3600000L, now));

        mockMvc.perform(post("/internal/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveBody())
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000001")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceCode").value("svc"))
                .andExpect(jsonPath("$.approverRef").value("approver"));
    }

    @Test
    @DisplayName("撤销返回 204")
    void revokeReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/internal/approvals/svc/inv/pkg/" + SHA)
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000002")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isNoContent());
        verify(approvalService).revoke("svc", "inv", "pkg", SHA);
    }

    @Test
    @DisplayName("未知字段被字段白名单拒绝")
    void unknownFieldRejected() throws Exception {
        mockMvc.perform(post("/internal/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceCode\":\"svc\",\"accessKey\":\"AKIA123\"}")
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000003")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAM"));
    }

    @Test
    @DisplayName("body 超限拒绝")
    void bodyTooLargeRejected() throws Exception {
        properties.setMaxBodyBytes(10);

        mockMvc.perform(post("/internal/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(50))
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000004")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAM"));
    }

    @Test
    @DisplayName("携带客户端凭据（Authorization）拒绝（统一 401 稳定消息）")
    void clientCredentialRejected() throws Exception {
        mockMvc.perform(post("/internal/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveBody())
                .header("Authorization", "Bearer client-token")
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000005")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("authentication failed"));
    }

    @Test
    @DisplayName("签名校验失败映射为统一 401 稳定消息")
    void authFailureMappedToUnauthorized() throws Exception {
        doThrow(new SmartomException(ErrorCode.APPROVAL_AUTH_FAILED, "bad signature")).when(verifier)
                .verify(anyString(), anyString(), any(byte[].class), anyString(), anyString(), anyString());

        mockMvc.perform(post("/internal/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveBody())
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000006")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("authentication failed"));
    }

    @Test
    @DisplayName("未分类异常映射为 500 稳定消息（不回显内部异常）")
    void genericExceptionMappedTo500() throws Exception {
        when(approvalService.approve("svc", "inv", "pkg", SHA, "approver", "reason"))
                .thenThrow(new RuntimeException("secret-internal-detail"));

        mockMvc.perform(post("/internal/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveBody())
                .header("X-Approval-Timestamp", "1")
                .header("X-Approval-Nonce", "nonce-000000000007")
                .header("X-Approval-Signature", "sig"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value("internal error"));
    }

    @Test
    @DisplayName("控制面不是 MCP 工具")
    void controlPlaneNotAnMcpTool() {
        assertThat(McpTool.class.isAssignableFrom(ApprovalControlPlaneController.class)).isFalse();
    }

    private String approveBody() {
        return "{\"serviceCode\":\"svc\",\"investigationId\":\"inv\",\"packageId\":\"pkg\","
                + "\"sha256\":\"" + SHA + "\",\"approverRef\":\"approver\",\"reason\":\"reason\"}";
    }
}
