/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import org.springframework.http.HttpStatus;

/**
 * 仅携带稳定错误编码的查询 API 异常。
 *
 * @author Codex
 * @since 2026-08-25
 */
public final class ProgressApiException extends RuntimeException {
    private final HttpStatus status;
    private final boolean resynchronize;

    /**
     * 创建安全 API 异常。
     *
     * @param status HTTP 状态
     * @param errorCode 稳定错误编码
     * @param resynchronize 是否要求重新同步
     */
    public ProgressApiException(HttpStatus status, String errorCode, boolean resynchronize) {
        super(errorCode);
        this.status = status;
        this.resynchronize = resynchronize;
    }

    /** @return HTTP 状态 */
    public HttpStatus status() {
        return status;
    }

    /** @return 稳定错误响应 */
    public ProgressApiError response() {
        return new ProgressApiError(getMessage(), resynchronize);
    }
}
