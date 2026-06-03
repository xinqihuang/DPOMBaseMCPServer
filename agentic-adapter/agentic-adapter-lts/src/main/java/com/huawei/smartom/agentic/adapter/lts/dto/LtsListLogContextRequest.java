/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

/**
 * {@code list_log_context} 工具的请求 DTO，对应华为云 LTS
 * {@code POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context}。
 *
 * <p>用法：先用 {@link LtsListLogsRequest} 拿到一条目标日志的 {@code lineNum} 与
 * {@code __time__} 游标值，再调本接口取其前后 N 条上下文。
 *
 * @param logGroupId      日志组 id（必填）
 * @param logStreamId     日志流 id（必填）
 * @param lineNum         目标日志的行号游标
 * @param cursorTime      目标日志的 {@code __time__} 时间戳字符串（与 SDK 的 {@code __time__} 对应）
 * @param backwardsSize   向前取多少条（[0, 500]）；可为 {@code null}，SDK 默认 100
 * @param forwardsSize    向后取多少条（[0, 500]）；可为 {@code null}，SDK 默认 100
 * @param scrollId        scroll 模式分页 id；可为 {@code null}
 * @author h00884391
 * @since 2026-06-03
 */
public record LtsListLogContextRequest(
        String logGroupId,
        String logStreamId,
        String lineNum,
        String cursorTime,
        Integer backwardsSize,
        Integer forwardsSize,
        String scrollId) {
}
