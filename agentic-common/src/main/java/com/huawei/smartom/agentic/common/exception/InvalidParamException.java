package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * Indicates locally-detected invalid input. Always non-retryable and carries no upstream trace id.
 */
public class InvalidParamException extends SmartomException {

    private static final long serialVersionUID = 1L;

    public InvalidParamException(String message) {
        super(ErrorCode.INVALID_PARAM, message, null, null);
    }
}
