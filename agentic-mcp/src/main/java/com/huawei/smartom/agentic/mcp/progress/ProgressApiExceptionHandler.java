/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 SSE 建连前错误映射为不泄露异常内容的响应。
 *
 * @author Codex
 * @since 2026-08-25
 */
@RestControllerAdvice(assignableTypes = InvestigationProgressController.class)
public class ProgressApiExceptionHandler {
    /**
     * 映射稳定查询错误。
     *
     * @param exception 查询异常
     * @return 安全错误响应
     */
    @ExceptionHandler(ProgressApiException.class)
    public ResponseEntity<ProgressApiError> handle(ProgressApiException exception) {
        return ResponseEntity.status(exception.status()).body(exception.response());
    }
}
