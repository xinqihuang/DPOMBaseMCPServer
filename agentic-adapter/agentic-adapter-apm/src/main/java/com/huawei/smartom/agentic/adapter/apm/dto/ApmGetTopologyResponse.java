/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code get_service_topology} 工具的响应 DTO。
 *
 * @param globalTraceId 全局 trace id
 * @param nodes         拓扑节点列表，可能为空但不会为 {@code null}
 * @param lines         拓扑边列表，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmGetTopologyResponse(
        String globalTraceId,
        List<ApmTopologyNode> nodes,
        List<ApmTopologyLine> lines) {
}
