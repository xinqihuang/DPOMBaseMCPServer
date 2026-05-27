/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.error;

/**
 * Unified error codes returned to MCP clients.
 *
 * <p>Each error code carries a {@code retryable} flag indicating whether the
 * MCP client (Agent) may safely retry the request.
 *
 * @author h00884391
 * @since 2026-05-21
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

    /**
     * Returns whether an operation that failed with this error code may be safely retried.
     *
     * @return {@code true} if the MCP client (Agent) may retry the request after backoff,
     *         {@code false} for terminal errors such as {@code INVALID_PARAM} or {@code UPSTREAM_AUTH_FAILED}
     */
    public boolean isRetryable() {
        return retryable;
    }
}
