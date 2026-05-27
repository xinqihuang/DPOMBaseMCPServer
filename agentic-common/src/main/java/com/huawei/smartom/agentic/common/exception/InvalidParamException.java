/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * Indicates locally-detected invalid input. Always non-retryable and carries no upstream trace id.
 *
 * @author h00884391
 * @since 2026-05-21
 */
public class InvalidParamException extends SmartomException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code InvalidParamException} with {@link ErrorCode#INVALID_PARAM}
     * and no upstream trace id or cause.
     *
     * @param message human-readable description of the validation failure
     */
    public InvalidParamException(String message) {
        super(ErrorCode.INVALID_PARAM, message, null, null);
    }
}
