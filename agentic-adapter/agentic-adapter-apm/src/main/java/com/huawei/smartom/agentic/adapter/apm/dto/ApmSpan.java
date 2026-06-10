/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.Map;

/**
 * APM 单条 span 记录，对应 SDK v1 {@code ClientSpanInfo} 的无损投影
 * （T19 PR-3 在 13 个 v1-era 字段基础上补齐 9 个 SDK 字段：
 * {@code globalPath} / {@code envId} / {@code instanceId} / {@code appId} / {@code bizId} /
 * {@code domainId} / {@code isAsync} / {@code type} / {@code bizCode}）。
 *
 * @param traceId       trace id
 * @param spanId        span id
 * @param globalTraceId 全局 trace id
 * @param source        入口资源（url / 方法名）
 * @param realSource    实际调用 url
 * @param className     类名
 * @param startTime     起始时间，毫秒
 * @param timeUsed      耗时，毫秒
 * @param code          状态码
 * @param hasError      是否有错误
 * @param errorReasons  错误原因摘要
 * @param httpMethod    HTTP 方法（仅 URL 监控项有值）
 * @param tags          自定义 tags 映射，可能为空但不会为 {@code null}
 * @param globalPath    全链路调用路径标识（v2 新增覆盖）
 * @param envId         APM 环境 ID（v2 新增覆盖）
 * @param instanceId    APM 实例 ID（v2 新增覆盖）
 * @param appId         APM 应用 ID（v2 新增覆盖）
 * @param bizId         业务 ID（v2 新增覆盖）
 * @param domainId      域 ID（v2 新增覆盖）
 * @param isAsync       是否异步调用（v2 新增覆盖）
 * @param type          span 类型字面量（v2 新增覆盖）
 * @param bizCode       业务码（v2 新增覆盖）
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmSpan(
        String traceId,
        String spanId,
        String globalTraceId,
        String source,
        String realSource,
        String className,
        Long startTime,
        Long timeUsed,
        Integer code,
        Boolean hasError,
        String errorReasons,
        String httpMethod,
        Map<String, String> tags,
        String globalPath,
        Long envId,
        Long instanceId,
        Long appId,
        Long bizId,
        Integer domainId,
        Boolean isAsync,
        String type,
        String bizCode) {
}
