/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 调用链拓扑边（从 {@code startNodeId} 指向 {@code endNodeId} 的调用）。
 *
 * <p>对应 SDK v1 {@code TraceTopologyLine}（7 个顶层字段）+ 嵌套
 * {@code TraceTopologyLineInfo}（client/server 各 4 字段）的无损投影。SDK 把 client / server
 * 两组同 shape 信息放在 {@code client_info} / {@code server_info} 嵌套对象里，本 DTO 沿用
 * 已有的 {@code client*} / {@code server*} 前缀风格继续平铺，避免破坏 Agent 兼容。
 *
 * <p>T19 PR-3 补齐之前丢失的 5 个 SDK 字段：{@code id} / {@code clientArgument} /
 * {@code clientEventId} / {@code serverArgument} / {@code serverEventId}。
 *
 * @param startNodeId      起始节点 id（{@code start_node_id}）
 * @param endNodeId        目标节点 id（{@code end_node_id}）
 * @param spanId           调用 span id
 * @param clientStartTime  客户端起始时间（毫秒，{@code client_info.start_time}）
 * @param clientTimeUsed   客户端耗时（毫秒，{@code client_info.time_used}）
 * @param serverStartTime  服务端起始时间（毫秒，{@code server_info.start_time}）
 * @param serverTimeUsed   服务端耗时（毫秒，{@code server_info.time_used}）
 * @param hint             边的提示信息
 * @param id               边记录 ID（PR-3 新增覆盖）
 * @param clientArgument   客户端参数信息（PR-3 新增覆盖，{@code client_info.argument}）
 * @param clientEventId    客户端事件 ID（PR-3 新增覆盖，{@code client_info.event_id}）
 * @param serverArgument   服务端参数信息（PR-3 新增覆盖，{@code server_info.argument}）
 * @param serverEventId    服务端事件 ID（PR-3 新增覆盖，{@code server_info.event_id}）
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmTopologyLine(
        Long startNodeId,
        Long endNodeId,
        String spanId,
        Long clientStartTime,
        Long clientTimeUsed,
        Long serverStartTime,
        Long serverTimeUsed,
        String hint,
        String id,
        String clientArgument,
        String clientEventId,
        String serverArgument,
        String serverEventId) {
}
