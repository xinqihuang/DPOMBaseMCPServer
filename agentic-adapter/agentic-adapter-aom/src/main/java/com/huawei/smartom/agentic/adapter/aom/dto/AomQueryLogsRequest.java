/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

/**
 * {@code query_logs} 工具的请求 DTO，对应华为云 AOM {@code ListLogItems} 接口的入参（{@code type=querylogs}）。
 *
 * <p>{@code category} 为受控枚举（T26，封闭集走 ADR-004 严格档）。
 *
 * @param category    日志类型
 * @param startTime   起始时间，毫秒 UTC 时间戳
 * @param endTime     结束时间，毫秒 UTC 时间戳
 * @param keyWord     关键字搜索，可选
 * @param pageSize    单页大小，可选（默认 100）
 * @param isDesc      是否按时间倒序返回，可选（默认 true）
 * @author h00884391
 * @since 2026-05-28
 */
public record AomQueryLogsRequest(
        AomLogCategory category,
        Long startTime,
        Long endTime,
        String keyWord,
        Integer pageSize,
        Boolean isDesc) {

    /**
     * 紧凑构造函数，设置默认值，不做业务校验。
     */
    public AomQueryLogsRequest {
        if (pageSize == null) {
            pageSize = 100;
        }
        if (isDesc == null) {
            isDesc = Boolean.TRUE;
        }
    }
}
