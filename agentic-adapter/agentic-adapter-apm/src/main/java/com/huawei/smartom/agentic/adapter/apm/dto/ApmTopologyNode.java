/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 调用链拓扑节点。
 *
 * @param nodeId   节点 id
 * @param nodeName 节点名称
 * @param hint     节点提示信息，可能为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmTopologyNode(
        Long nodeId,
        String nodeName,
        String hint) {
}
