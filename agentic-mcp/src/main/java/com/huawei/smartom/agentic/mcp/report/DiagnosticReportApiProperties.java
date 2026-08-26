/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * diagnosis-only 报告 API 的默认关闭配置。
 *
 * @param generationEnabled 是否允许生成
 * @param readApiEnabled 是否允许读取和重放
 * @param token 独立 API token
 * @author Codex
 * @since 2026-08-26
 */
@ConfigurationProperties("dpom.diagnostic-report")
public record DiagnosticReportApiProperties(boolean generationEnabled, boolean readApiEnabled, String token) {
    public DiagnosticReportApiProperties {
        token = token == null ? "" : token;
    }
}
