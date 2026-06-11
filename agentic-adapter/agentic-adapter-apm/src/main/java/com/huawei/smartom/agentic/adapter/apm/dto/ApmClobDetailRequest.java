/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code show_clob_detail} 工具的请求 DTO，对应华为云 APM {@code ShowClobDetail}
 * 接口（POST /v1/apm2/openapi/view/metric/get-clob-detail）的入参。
 *
 * <p>{@code clobId} 必须来自 {@code show_event_detail} / {@code show_trace_events}
 * 响应中出现的 clob 引用（CLAUDE.md §4.3 (b)）。
 *
 * @param businessId 应用 id（{@code x-business-id} 头），可为 {@code null}（adapter 回落配置）
 * @param envId      事件所属环境 id，必填
 * @param clobId     clob 引用 id，必填
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmClobDetailRequest(
        Long businessId,
        Long envId,
        String clobId) {
}
