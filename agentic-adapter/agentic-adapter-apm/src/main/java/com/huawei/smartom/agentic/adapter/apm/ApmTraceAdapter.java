/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
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

    /**
     * 获取一个 trace 的全部调用链事件序列（{@code ShowTraceEvents}，T29）。
     *
     * @param traceId trace id，不能为空白
     * @return 事件序列（无损覆盖 SDK 全部字段）
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    ApmTraceEventsResponse showTraceEvents(String traceId);

    /**
     * 获取单个调用链事件的详情（{@code ShowEventDetail}，T29）。
     *
     * @param request 四元组请求（trace/span/event/env），不能为 null
     * @return 事件详情（无损覆盖 SDK 全部字段）
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    ApmEventDetailResponse showEventDetail(ApmEventDetailRequest request);

    /**
     * 按 clob 引用取回超长字段全文（{@code ShowClobDetail}，T29）。
     *
     * <p>{@code businessId} 为 {@code null} 时回落到配置默认值。
     *
     * @param request clob 请求，不能为 null
     * @return clob 全文
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    ApmClobDetailResponse showClobDetail(ApmClobDetailRequest request);
}
