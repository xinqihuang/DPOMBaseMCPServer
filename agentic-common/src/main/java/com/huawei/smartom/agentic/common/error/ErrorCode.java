/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.error;

/**
 * 返回给 MCP 客户端的统一错误码。
 *
 * <p>每个错误码都携带 {@code retryable} 标记，指示 MCP 客户端（Agent）能否安全地重试请求。
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
     * 返回以本错误码失败的操作是否可以安全重试。
     *
     * @return {@code true} 表示 MCP 客户端（Agent）可以在退避后重试该请求；
     *         {@code false} 表示终止性错误，例如 {@code INVALID_PARAM} 或 {@code UPSTREAM_AUTH_FAILED}
     */
    public boolean isRetryable() {
        return retryable;
    }
}
