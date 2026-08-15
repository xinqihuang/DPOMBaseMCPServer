/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.sdk;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;

import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import org.springframework.stereotype.Component;

/**
 * 将华为云 SDK 异常映射为统一的 {@link SmartomException}。
 *
 * <p>映射规则（按顺序匹配）：
 * <ul>
 *   <li>Resilience4j {@code RequestNotPermitted} -&gt; {@link ErrorCode#UPSTREAM_THROTTLED}
 *   <li>{@code ServiceResponseException} 状态码为 429 -&gt; {@link ErrorCode#UPSTREAM_THROTTLED}
 *   <li>{@code ServiceResponseException} 状态码为 401 / 403 -&gt; {@link ErrorCode#UPSTREAM_AUTH_FAILED}
 *   <li>{@code ServiceResponseException} 状态码为 5xx（包含 500-599）-&gt; {@link ErrorCode#UPSTREAM_ERROR}
 *   <li>其他 {@code ServiceResponseException} -&gt; {@link ErrorCode#UPSTREAM_ERROR}
 *   <li>{@code RequestTimeoutException} / {@code ConnectionException} -&gt; {@link ErrorCode#TIMEOUT}
 *   <li>本服务自身抛出的 {@code SmartomException} -&gt; 透传（不重复包裹）
 *   <li>其他异常 -&gt; {@link ErrorCode#INTERNAL}
 * </ul>
 *
 * <p>华为云的 {@code X-Request-Id} 通过 {@code ServiceResponseException#getRequestId()} 提取。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class SdkExceptionMapper {

    /**
     * 将 SDK 调用链中抛出的任意 throwable 映射为 {@link SmartomException}。
     *
     * @param throwable 原始 throwable，不可为 null
     * @return 一个保留原始 cause 的 {@link SmartomException}（可能为 {@link UpstreamException}）
     */
    public SmartomException map(Throwable throwable) {
        if (throwable == null) {
            return new SmartomException(ErrorCode.INTERNAL, "null throwable", null, null);
        }
        if (throwable instanceof SmartomException already) {
            return already;
        }
        if (throwable instanceof RequestNotPermitted) {
            return new UpstreamException(
                    ErrorCode.UPSTREAM_THROTTLED,
                    "Local rate limiter rejected the request: " + throwable.getMessage(),
                    null,
                    throwable);
        }
        if (throwable instanceof ServiceResponseException sre) {
            return mapServiceResponse(sre);
        }
        if (throwable instanceof RequestTimeoutException) {
            return new UpstreamException(ErrorCode.TIMEOUT, safeMessage(throwable), null, throwable);
        }
        if (throwable instanceof ConnectionException) {
            return new UpstreamException(ErrorCode.TIMEOUT, safeMessage(throwable), null, throwable);
        }
        return new SmartomException(ErrorCode.INTERNAL, safeMessage(throwable), null, throwable);
    }

    private SmartomException mapServiceResponse(ServiceResponseException sre) {
        int status = sre.getHttpStatusCode();
        String traceId = sre.getRequestId();
        String message = safeMessage(sre);
        ErrorCode code = classify(status);
        return new UpstreamException(code, message, traceId, sre);
    }

    private ErrorCode classify(int status) {
        if (status == 429) {
            return ErrorCode.UPSTREAM_THROTTLED;
        }
        if (status == 401 || status == 403) {
            return ErrorCode.UPSTREAM_AUTH_FAILED;
        }
        if (status >= 500 && status <= 599) {
            return ErrorCode.UPSTREAM_ERROR;
        }
        if (status >= 400 && status <= 499) {
            return ErrorCode.UPSTREAM_INVALID_PARAM;
        }
        return ErrorCode.UPSTREAM_ERROR;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
