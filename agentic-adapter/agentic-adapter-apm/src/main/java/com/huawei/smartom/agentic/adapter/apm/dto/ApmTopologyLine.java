/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 调用链拓扑边（从 {@code startNodeId} 指向 {@code endNodeId} 的调用）。
 *
 * @param startNodeId      起始节点 id
 * @param endNodeId        目标节点 id
 * @param spanId           调用 span id
 * @param clientStartTime  客户端起始时间（毫秒），可能为 {@code null}
 * @param clientTimeUsed   客户端耗时（毫秒），可能为 {@code null}
 * @param serverStartTime  服务端起始时间（毫秒），可能为 {@code null}
 * @param serverTimeUsed   服务端耗时（毫秒），可能为 {@code null}
 * @param hint             边的提示信息，可能为 {@code null}
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
        String hint) {
}
