/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code get_service_topology} 工具的请求 DTO，对应华为云 APM {@code ShowTopology} 接口。
 *
 * @param traceId 调用链 traceId
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmGetTopologyRequest(String traceId) {
}
