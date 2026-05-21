package com.huawei.smartom.agentic.common.error;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured error payload returned by MCP tools.
 *
 * <p>JSON field names use snake_case to align with the Huawei Cloud API style.
 *
 * @param errorCode        machine-readable error code (see {@link ErrorCode})
 * @param errorMessage     human-readable description
 * @param upstreamTraceId  Huawei Cloud X-Request-Id (nullable when error originates locally)
 * @param retryable        whether the caller may retry
 */
public record ErrorResponse(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("upstream_trace_id") String upstreamTraceId,
        @JsonProperty("retryable") boolean retryable) {

    /**
     * Convenience factory deriving {@code errorCode} string and {@code retryable} from the enum.
     *
     * @param code             the error code
     * @param message          human-readable message
     * @param upstreamTraceId  upstream trace id (nullable)
     * @return populated ErrorResponse
     */
    public static ErrorResponse of(ErrorCode code, String message, String upstreamTraceId) {
        return new ErrorResponse(code.name(), message, upstreamTraceId, code.isRetryable());
    }
}
