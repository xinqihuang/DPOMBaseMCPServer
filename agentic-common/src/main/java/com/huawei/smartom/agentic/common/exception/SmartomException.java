/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * Base business exception for the DPOMBaseMCPServer.
 *
 * <p>All exceptions thrown across module boundaries should extend this class.
 * Carries a structured {@link ErrorCode} and the upstream trace id (when applicable)
 * so MCP tools can render a stable error payload regardless of where the failure originated.
 *
 * @author h00884391
 * @since 2026-05-21
 */
public class SmartomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String upstreamTraceId;

    /**
     * Full constructor.
     *
     * @param errorCode        the unified error code, must not be null
     * @param message          human-readable message
     * @param upstreamTraceId  Huawei Cloud X-Request-Id, may be null when error is local
     * @param cause            the underlying cause, may be null
     * @throws IllegalArgumentException if {@code errorCode} is {@code null}
     */
    public SmartomException(ErrorCode errorCode, String message, String upstreamTraceId, Throwable cause) {
        super(message, cause);
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        this.errorCode = errorCode;
        this.upstreamTraceId = upstreamTraceId;
    }

    /**
     * Local-error convenience constructor (no upstream trace id).
     *
     * @param errorCode the unified error code
     * @param message   human-readable message
     */
    public SmartomException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    /**
     * Returns the structured error code carried by this exception.
     *
     * @return the {@link ErrorCode}, never {@code null}
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @return Huawei Cloud upstream X-Request-Id, may be {@code null}
     */
    public String getUpstreamTraceId() {
        return upstreamTraceId;
    }
}
