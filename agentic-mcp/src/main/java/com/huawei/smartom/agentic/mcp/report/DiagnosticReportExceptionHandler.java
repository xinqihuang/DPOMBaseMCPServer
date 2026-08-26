/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 只向报告 API 返回稳定错误码。
 *
 * @author Codex
 * @since 2026-08-26
 */
@RestControllerAdvice(assignableTypes = DiagnosticReportController.class)
public class DiagnosticReportExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ErrorBody> unauthorized() {
        return response(HttpStatus.UNAUTHORIZED, "REPORT_AUTHENTICATION_FAILED");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> missing() {
        return response(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorBody> invalid(RuntimeException error) {
        String code = error.getMessage() != null && error.getMessage().matches("REPORT_[A-Z0-9_]{1,80}")
                ? error.getMessage() : "REPORT_REQUEST_REJECTED";
        return response(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }

    private ResponseEntity<ErrorBody> response(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(new ErrorBody(code));
    }

    /**
     * 安全错误体。
     *
     * @param code 稳定错误码
     * @author Codex
     * @since 2026-08-26
     */
    public record ErrorBody(String code) { }
}
