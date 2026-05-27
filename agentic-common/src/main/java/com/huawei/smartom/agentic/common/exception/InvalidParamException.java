/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * 表示本地校验到的非法入参。始终不可重试，且不携带上游 trace id。
 *
 * @author h00884391
 * @since 2026-05-21
 */
public class InvalidParamException extends SmartomException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造一个新的 {@code InvalidParamException}，错误码为 {@link ErrorCode#INVALID_PARAM}，
     * 不携带上游 trace id 也不携带 cause。
     *
     * @param message 可读的校验失败描述
     */
    public InvalidParamException(String message) {
        super(ErrorCode.INVALID_PARAM, message, null, null);
    }
}
