/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.Map;

/**
 * APM 单条 span 摘要。
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
        Map<String, String> tags) {
}
