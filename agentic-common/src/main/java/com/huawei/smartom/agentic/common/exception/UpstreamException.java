package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * Indicates a failure originating from a Huawei Cloud upstream service.
 *
 * <p>The {@link ErrorCode} narrows the failure type
 * (throttled / auth-failed / 5xx / timeout / etc).
 */
public class UpstreamException extends SmartomException {

    private static final long serialVersionUID = 1L;

    public UpstreamException(ErrorCode errorCode, String message, String upstreamTraceId, Throwable cause) {
        super(errorCode, message, upstreamTraceId, cause);
    }
}
