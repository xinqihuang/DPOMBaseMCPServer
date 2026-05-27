/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 用于查询 APM 调用链与拓扑的端口接口。
 *
 * <p>实现类负责封装华为云 APM SDK，并将 SDK 类型转换为本项目自定义的 DTO。SDK 异常必须在离开实现之前
 * 被映射为 {@link SmartomException}。
 *
 * @author h00884391
 * @since 2026-05-28
 */
public interface ApmTraceAdapter {

    /**
     * 查询符合条件的 APM span 列表（{@code ShowSpanSearch}）。
     *
     * @param request 查询请求，不能为 null
     * @return span 摘要列表
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    ApmQueryTracesResponse queryTraces(ApmQueryTracesRequest request);

    /**
     * 查询指定 traceId 的调用链拓扑（{@code ShowTopology}）。
     *
     * @param request 查询请求，不能为 null
     * @return 拓扑节点与边
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    ApmGetTopologyResponse getTopology(ApmGetTopologyRequest request);
}
