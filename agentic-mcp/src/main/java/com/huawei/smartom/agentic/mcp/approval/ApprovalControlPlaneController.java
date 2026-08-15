/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalProperties;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalRecord;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * 证据转移审批控制面（内部 REST，非 MCP）。
 *
 * <p>仅批准/撤销，默认关闭、独立 gate；每个请求做 HMAC 签名校验、客户端 AK/SK 拒绝与 body 限长；不暴露给 LLM。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@RestController
@RequestMapping("/internal/approvals")
@ConditionalOnProperty(name = "dpom.approval.enabled", havingValue = "true")
public class ApprovalControlPlaneController {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalControlPlaneController.class);

    private final ApprovalService approvalService;
    private final ApprovalSignatureVerifier verifier;
    private final ApprovalProperties properties;
    private final ObjectMapper mapper;

    /**
     * 构造控制面控制器。
     *
     * @param approvalService 审批编排
     * @param verifier        HMAC 签名校验器
     * @param properties      审批配置（max-body-bytes）
     */
    public ApprovalControlPlaneController(ApprovalService approvalService, ApprovalSignatureVerifier verifier,
            ApprovalProperties properties) {
        this.approvalService = approvalService;
        this.verifier = verifier;
        this.properties = properties;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    /**
     * 批准一次精确 OBS 上传。
     *
     * @param request HTTP 请求（读取原始 body 与签名头）
     * @return 已创建的审批（201）
     */
    @PostMapping
    public ResponseEntity<ApprovalRecordResponse> approve(HttpServletRequest request) {
        byte[] body = readBody(request);
        rejectClientCredentials(request);
        verify(request, body);
        ApprovalCreateRequest createRequest = parseBody(body);
        ApprovalRecord record = approvalService.approve(createRequest.serviceCode(), createRequest.investigationId(),
                createRequest.packageId(), createRequest.sha256(), createRequest.approverRef(), createRequest.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApprovalRecordResponse(record.serviceCode(), record.investigationId(), record.packageId(),
                        record.sha256(), record.approverRef(), record.expiresAtMillis()));
    }

    /**
     * 撤销一次审批。
     *
     * @param request         HTTP 请求（读取原始 body 与签名头）
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     * @return 204 无内容
     */
    @DeleteMapping("/{serviceCode}/{investigationId}/{packageId}/{sha256}")
    public ResponseEntity<Void> revoke(HttpServletRequest request, @PathVariable String serviceCode,
            @PathVariable String investigationId, @PathVariable String packageId, @PathVariable String sha256) {
        byte[] body = readBody(request);
        rejectClientCredentials(request);
        verify(request, body);
        approvalService.revoke(serviceCode, investigationId, packageId, sha256);
        return ResponseEntity.noContent().build();
    }

    /**
     * 将业务异常映射为稳定错误码的 HTTP 响应。
     *
     * @param exception 业务异常
     * @return 携带 errorCode/message 的响应
     */
    @ExceptionHandler(SmartomException.class)
    public ResponseEntity<ApprovalErrorResponse> handleError(SmartomException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(statusOf(errorCode))
                .body(new ApprovalErrorResponse(errorCode.name(), messageOf(errorCode)));
    }

    /**
     * 将未分类异常映射为稳定 500 响应（不回显内部异常）。
     *
     * @param exception 未分类异常
     * @return 携带 INTERNAL 稳定消息的响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApprovalErrorResponse> handleUnexpected(Exception exception) {
        LOG.error("approval control plane unexpected error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApprovalErrorResponse(ErrorCode.INTERNAL.name(), "internal error"));
    }

    private byte[] readBody(HttpServletRequest request) {
        try (InputStream input = request.getInputStream()) {
            byte[] body = input.readNBytes(properties.getMaxBodyBytes() + 1);
            if (body.length > properties.getMaxBodyBytes()) {
                throw new SmartomException(ErrorCode.INVALID_PARAM, "request body exceeds limit");
            }
            return body;
        }
        catch (IOException exception) {
            throw new SmartomException(ErrorCode.INTERNAL, "failed to read request body", null, exception);
        }
    }

    private void rejectClientCredentials(HttpServletRequest request) {
        if (request.getHeader("Authorization") != null || request.getHeader("X-Access-Key") != null
                || request.getHeader("X-Secret-Key") != null) {
            throw new SmartomException(ErrorCode.APPROVAL_AUTH_FAILED, "client credentials are not allowed");
        }
    }

    private void verify(HttpServletRequest request, byte[] body) {
        verifier.verify(request.getMethod(), request.getRequestURI(), body,
                request.getHeader("X-Approval-Timestamp"), request.getHeader("X-Approval-Nonce"),
                request.getHeader("X-Approval-Signature"));
    }

    private ApprovalCreateRequest parseBody(byte[] body) {
        try {
            return mapper.readValue(body, ApprovalCreateRequest.class);
        }
        catch (IOException exception) {
            throw new SmartomException(ErrorCode.INVALID_PARAM, "invalid request body");
        }
    }

    private HttpStatus statusOf(ErrorCode errorCode) {
        if (errorCode == ErrorCode.APPROVAL_AUTH_FAILED) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (errorCode == ErrorCode.APPROVAL_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (errorCode == ErrorCode.INVALID_PARAM) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode == ErrorCode.APPROVAL_STORAGE_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String messageOf(ErrorCode errorCode) {
        switch (errorCode) {
            case APPROVAL_AUTH_FAILED:
                return "authentication failed";
            case APPROVAL_NOT_FOUND:
                return "approval not found";
            case INVALID_PARAM:
                return "invalid request";
            case APPROVAL_STORAGE_ERROR:
                return "approval storage unavailable";
            default:
                return "internal error";
        }
    }
}
