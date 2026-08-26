/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * diagnosis-only 报告 API 的独立认证器。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Component
public class DiagnosticReportAuthorization {
    private final DiagnosticReportApiProperties properties;
    /**
     * 创建认证器。
     *
     * @param properties 报告配置
     */
    public DiagnosticReportAuthorization(DiagnosticReportApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 验证固定长度无关的 token，不记录其内容。
     *
     * @param received 收到的 token
     * @throws SecurityException 认证失败
     */
    public void require(String received) {
        String configured = properties.token();
        if (configured.isBlank() || received == null || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("REPORT_AUTHENTICATION_FAILED");
        }
    }
}
