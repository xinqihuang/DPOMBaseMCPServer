/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * DPOMBaseMCPServer 的业务异常基类。
 *
 * <p>所有跨模块边界抛出的异常都应继承本类。它携带结构化的 {@link ErrorCode} 以及上游 trace id
 * （如有），从而无论故障源自何处，MCP tool 都能输出稳定一致的错误负载。
 *
 * @author h00884391
 * @since 2026-05-21
 */
public class SmartomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String upstreamTraceId;

    /**
     * 全参构造函数。
     *
     * @param errorCode        统一错误码，不可为 null
     * @param message          可读的错误消息
     * @param upstreamTraceId  华为云 X-Request-Id，错误源自本地时可为 null
     * @param cause            底层 cause，可为 null
     * @throws IllegalArgumentException 当 {@code errorCode} 为 {@code null}
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
     * 本地错误的便捷构造函数（不携带上游 trace id）。
     *
     * @param errorCode 统一错误码
     * @param message   可读的错误消息
     */
    public SmartomException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    /**
     * 返回本异常携带的结构化错误码。
     *
     * @return {@link ErrorCode}，不会为 {@code null}
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @return 华为云上游的 X-Request-Id，可能为 {@code null}
     */
    public String getUpstreamTraceId() {
        return upstreamTraceId;
    }
}
