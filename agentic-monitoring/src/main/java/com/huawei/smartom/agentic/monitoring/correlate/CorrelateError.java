/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.correlate;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * {@code correlate_incident} 分支错误描述。
 *
 * @param errorCode        统一错误码
 * @param retryable        是否可重试
 * @param message          错误消息
 * @param upstreamTraceId  上游 trace id，可能为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record CorrelateError(
        ErrorCode errorCode,
        boolean retryable,
        String message,
        String upstreamTraceId) {
}
