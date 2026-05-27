/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.error;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MCP tool 返回的结构化错误负载。
 *
 * <p>JSON 字段名采用 snake_case，与华为云 API 风格保持一致。
 *
 * @param errorCode        机器可读的错误码（参见 {@link ErrorCode}）
 * @param errorMessage     可读的错误描述
 * @param upstreamTraceId  华为云 X-Request-Id（错误源自本地时可为 null）
 * @param retryable        调用方是否可重试
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record ErrorResponse(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("upstream_trace_id") String upstreamTraceId,
        @JsonProperty("retryable") boolean retryable) {

    /**
     * 便捷工厂方法，从枚举派生 {@code errorCode} 字符串和 {@code retryable} 字段。
     *
     * @param code             错误码
     * @param message          可读的错误消息
     * @param upstreamTraceId  上游 trace id（可为 null）
     * @return 已填充字段的 ErrorResponse
     */
    public static ErrorResponse of(ErrorCode code, String message, String upstreamTraceId) {
        return new ErrorResponse(code.name(), message, upstreamTraceId, code.isRetryable());
    }
}
