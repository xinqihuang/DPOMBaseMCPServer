/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
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
 * Maps Huawei Cloud SDK exceptions to the unified {@link SmartomException}.
 *
 * <p>Rules (in order):
 * <ul>
 *   <li>Resilience4j {@code RequestNotPermitted} -&gt; {@link ErrorCode#UPSTREAM_THROTTLED}
 *   <li>{@code ServiceResponseException} with status 429 -&gt; {@link ErrorCode#UPSTREAM_THROTTLED}
 *   <li>{@code ServiceResponseException} with status 401 / 403 -&gt; {@link ErrorCode#UPSTREAM_AUTH_FAILED}
 *   <li>{@code ServiceResponseException} with status 5xx (incl. 500-599) -&gt; {@link ErrorCode#UPSTREAM_ERROR}
 *   <li>Other {@code ServiceResponseException} -&gt; {@link ErrorCode#UPSTREAM_ERROR}
 *   <li>{@code RequestTimeoutException} / {@code ConnectionException} -&gt; {@link ErrorCode#TIMEOUT}
 *   <li>{@code SmartomException} thrown by ourselves -&gt; pass through (no double-wrap)
 *   <li>Anything else -&gt; {@link ErrorCode#INTERNAL}
 * </ul>
 *
 * <p>The Huawei Cloud {@code X-Request-Id} is extracted via {@code ServiceResponseException#getRequestId()}.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class SdkExceptionMapper {

    /**
     * Map an arbitrary throwable from the SDK call path to a {@link SmartomException}.
     *
     * @param throwable the original throwable, must not be null
     * @return a {@link SmartomException} (or {@link UpstreamException}) preserving the cause
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
