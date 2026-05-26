/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * Indicates a failure originating from a Huawei Cloud upstream service.
 *
 * <p>The {@link ErrorCode} narrows the failure type
 * (throttled / auth-failed / 5xx / timeout / etc).
 *
 * @author h00884391
 * @since 2026-05-21
 */
public class UpstreamException extends SmartomException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code UpstreamException} representing a failure from a Huawei Cloud service.
     *
     * @param errorCode       the classified error code (throttled / auth-failed / upstream-error / timeout)
     * @param message         human-readable description of the failure
     * @param upstreamTraceId the Huawei Cloud {@code X-Request-Id}, may be {@code null} when unavailable
     * @param cause           the underlying SDK exception, may be {@code null}
     */
    public UpstreamException(ErrorCode errorCode, String message, String upstreamTraceId, Throwable cause) {
        super(errorCode, message, upstreamTraceId, cause);
    }
}
