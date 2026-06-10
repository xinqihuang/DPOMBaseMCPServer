/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code show_apm_trend} 工具的请求 DTO，对应华为云 APM
 * {@code POST /v1/apm2/openapi/view/trend/show}。
 *
 * <p>{@code viewConfig} 直接持有嵌套 {@link ApmTrendViewConfig} 对象（不拍平），
 * Spring AI MCP 会从 record 反射出 JSON Schema 供 Agent 调用。
 *
 * @param businessId    APM 业务 id（HTTP {@code x-business-id} 头）；为 {@code null}
 *                      时回落到 {@code huaweicloud.apm-business-id} 配置项默认值
 * @param viewConfig    视图配置，必填非 {@code null}
 * @param instanceId    APM 实例 id，可选
 * @param monitorItemId 监控项 id，可选
 * @param envId         环境 id，可选
 * @param startTime     开始时间字符串，必填；原样透传给上游
 * @param endTime       结束时间字符串，必填；原样透传给上游
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmTrendRequest(
        Long businessId,
        ApmTrendViewConfig viewConfig,
        Long instanceId,
        Long monitorItemId,
        Long envId,
        String startTime,
        String endTime) {
}
