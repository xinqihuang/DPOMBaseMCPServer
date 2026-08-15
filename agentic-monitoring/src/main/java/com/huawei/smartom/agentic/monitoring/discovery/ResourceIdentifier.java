/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

/**
 * 单个资源标识及其 provenance。
 *
 * <p>{@code sourceTool} 与 {@code sourceApi} 语义不同：前者是发起该取值的 MCP 工具或
 * {@code USER_PROVIDED}（用户/Agent 提供的锚点），后者是真实 adapter/华为云 API 标识
 * （如 {@code ApmSearchApplication}、{@code LtsListLogStreams}）或 {@code USER_PROVIDED}。
 * 用户输入不得冒充上游验证后的 EXACT。
 *
 * @param name        标识名，如 {@code region}、{@code cluster_id}、{@code apm_business_id}
 * @param value       标识值，不可为 {@code null}
 * @param sourceTool  取值来源工具，或 {@code USER_PROVIDED}
 * @param sourceApi   真实 adapter/华为云 API 标识，或 {@code USER_PROVIDED}
 * @param observedAt  观测时间（epoch 毫秒）
 * @param kind        取值性质（EXACT / DERIVED）
 * @param ambiguous   是否多候选/歧义
 *
 * @author h00884391
 * @since 2026-08-16
 */
public record ResourceIdentifier(String name, String value, String sourceTool, String sourceApi,
        long observedAt, IdentifierKind kind, boolean ambiguous) {
}
