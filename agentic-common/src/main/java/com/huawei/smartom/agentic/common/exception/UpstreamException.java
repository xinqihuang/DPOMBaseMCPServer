/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.exception;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * 表示源自华为云上游服务的故障。
 *
 * <p>{@link ErrorCode} 进一步细分故障类型（限流 / 鉴权失败 / 5xx / 超时 等）。
 *
 * @author h00884391
 * @since 2026-05-21
 */
public class UpstreamException extends SmartomException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造一个新的 {@code UpstreamException}，表示来自华为云服务的故障。
     *
     * @param errorCode       分类后的错误码（限流 / 鉴权失败 / 上游错误 / 超时）
     * @param message         可读的故障描述
     * @param upstreamTraceId 华为云返回的 {@code X-Request-Id}，不可获取时可为 {@code null}
     * @param cause           底层 SDK 异常，可为 {@code null}
     */
    public UpstreamException(ErrorCode errorCode, String message, String upstreamTraceId, Throwable cause) {
        super(errorCode, message, upstreamTraceId, cause);
    }
}
