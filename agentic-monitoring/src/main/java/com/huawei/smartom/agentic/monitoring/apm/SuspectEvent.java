/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import java.util.List;
import java.util.Map;

/**
 * 一个被诊断挑出的可疑调用链事件（出错或最慢的 span），已下钻其详情与 clob。
 *
 * <p>定位坐标 {@code (traceId, spanId, eventId, envId)} 与原始 {@code show_event_detail}
 * 入参一致；{@code tags} 为详情返回的完整标签，{@code clobs} 为其中 {@code *_clob_id}
 * 解析出的全文（堆栈 / SQL）。
 *
 * @param spanId         span id
 * @param eventId        event id
 * @param envId          环境 id（clob 下钻所需）
 * @param type           事件类型，如 {@code SQL} / {@code HTTP}
 * @param method         方法名
 * @param className      类名
 * @param timeUsed       耗时（毫秒）
 * @param code           状态码
 * @param hasError       是否标记出错
 * @param errorReasons   出错原因摘要
 * @param selectedReason 被挑出的原因：{@code error}（出错）或 {@code slow}（最慢）
 * @param tags           详情返回的完整 tags；当详情下钻失败时为 {@code null}
 * @param clobs          已解析的 clob 全文列表，不会为 {@code null}（无则空列表）
 * @author huangxinqi
 * @since 2026-06-18
 */
public record SuspectEvent(
        String spanId,
        String eventId,
        Long envId,
        String type,
        String method,
        String className,
        Long timeUsed,
        Integer code,
        Boolean hasError,
        String errorReasons,
        String selectedReason,
        Map<String, String> tags,
        List<ResolvedClob> clobs) {
}
