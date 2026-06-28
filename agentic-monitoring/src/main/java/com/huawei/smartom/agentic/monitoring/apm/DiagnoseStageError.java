/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.common.error.ErrorCode;

/**
 * {@code diagnose_trace} 编排过程中某个阶段的非致命错误。
 *
 * <p>编排各阶段（拓扑 / 事件 / 详情 / clob）相互独立：单个阶段失败不会中断整体，
 * 仅在响应的 {@code errors} 列表中记录一条，便于 Agent 知晓诊断包的哪一部分缺失。
 *
 * @param stage           出错阶段标识，如 {@code topology} / {@code events} / {@code detail:span-1} / {@code clob:clob-777}
 * @param errorCode       统一错误码
 * @param retryable       是否可重试
 * @param message         简要错误消息
 * @param upstreamTraceId 华为云返回的 {@code X-Request-Id}，可能为 {@code null}
 * @author huangxinqi
 * @since 2026-06-18
 */
public record DiagnoseStageError(
        String stage,
        ErrorCode errorCode,
        boolean retryable,
        String message,
        String upstreamTraceId) {
}
