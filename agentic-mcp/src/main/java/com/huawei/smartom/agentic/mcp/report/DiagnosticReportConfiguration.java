/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Phase 5 diagnosis-only 报告配置。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiagnosticReportApiProperties.class)
public class DiagnosticReportConfiguration { }
