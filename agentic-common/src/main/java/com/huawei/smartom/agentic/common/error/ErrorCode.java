package com.huawei.smartom.agentic.common.error;

/**
 * Unified error codes returned to MCP clients.
 *
 * <p>Each error code carries a {@code retryable} flag indicating whether the
 * MCP client (Agent) may safely retry the request.
 */
public enum ErrorCode {

    /** Input validation failed locally; never retryable. */
    INVALID_PARAM(false),

    /** Upstream rate-limited (e.g., HTTP 429). Safe to retry after backoff. */
    UPSTREAM_THROTTLED(true),

    /** Upstream authentication / authorization failed (HTTP 401 / 403). */
    UPSTREAM_AUTH_FAILED(false),

    /** Upstream server error (HTTP 5xx). */
    UPSTREAM_ERROR(true),

    /** Call timeout. */
    TIMEOUT(true),

    /** Serialization or otherwise unclassified internal error. */
    INTERNAL(false);

    private final boolean retryable;

    ErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
