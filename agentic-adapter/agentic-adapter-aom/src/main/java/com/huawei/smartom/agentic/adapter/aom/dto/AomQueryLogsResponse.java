/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

/**
 * {@code query_logs} 工具的响应 DTO。
 *
 * <p>由于 AOM {@code ListLogItems} 响应的 {@code result} 字段是一段未结构化的 JSON 字符串，
 * 此处保持原始字符串透传给上层调用方进一步解析，避免在 SDK 升级时频繁调整数据结构。
 *
 * @param result       上游 {@code result} JSON 字符串，可能为 {@code null}
 * @param errorCode    上游响应码（{@code SVCSTG_AMS_2000000} 表示成功）
 * @param errorMessage 上游响应信息描述，可能为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record AomQueryLogsResponse(
        String result,
        String errorCode,
        String errorMessage) {
}
