/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmTraceAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEventDetailResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmQueryTracesResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTraceEventsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.stereotype.Service;

/**
 * APM 调用链与拓扑查询的业务编排。
 *
 * <p>对 {@code query_traces} / {@code get_service_topology} 以及 T29 根因诊断链三工具
 * （{@code show_trace_events} / {@code show_event_detail} / {@code show_clob_detail}）
 * 的入参进行规约校验，校验通过后委托 adapter 调用 SDK。诊断链全部为实时数据，不缓存。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class ApmTraceService {

    private static final int PAGE_MIN = 1;
    private static final int PAGE_SIZE_MIN = 1;
    private static final int PAGE_SIZE_MAX = 500;

    private final ApmTraceAdapter adapter;

    /**
     * 构造一个 {@code ApmTraceService}。
     *
     * @param adapter 执行实际 SDK 调用的 APM adapter
     */
    public ApmTraceService(ApmTraceAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行 trace 搜索。
     *
     * @param request span 搜索请求，不能为 null
     * @return 命中 span 列表
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public ApmQueryTracesResponse queryTraces(ApmQueryTracesRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validateTraces(request);
        return adapter.queryTraces(request);
    }

    /**
     * 校验入参后委托 adapter 获取调用链拓扑。
     *
     * @param request 拓扑查询请求，不能为 null
     * @return 拓扑节点与边
     * @throws InvalidParamException {@code traceId} 缺失时抛出
     */
    public ApmGetTopologyResponse getTopology(ApmGetTopologyRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new InvalidParamException("trace_id is required");
        }
        return adapter.getTopology(request);
    }

    /**
     * 校验入参后委托 adapter 获取一个 trace 的全部调用链事件（T29）。
     *
     * @param traceId trace id，不能为空白
     * @return 事件序列
     * @throws InvalidParamException {@code traceId} 缺失或空白时抛出
     */
    public ApmTraceEventsResponse showTraceEvents(String traceId) {
        Validations.requireNonBlank(traceId, "trace_id");
        return adapter.showTraceEvents(traceId);
    }

    /**
     * 校验入参后委托 adapter 获取单个调用链事件详情（T29）。
     *
     * @param request 四元组请求，不能为 null，四个参数全部必填
     * @return 事件详情
     * @throws InvalidParamException 任一入参缺失/空白时抛出
     */
    public ApmEventDetailResponse showEventDetail(ApmEventDetailRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        Validations.requireNonBlank(request.traceId(), "trace_id");
        Validations.requireNonBlank(request.spanId(), "span_id");
        Validations.requireNonBlank(request.eventId(), "event_id");
        Validations.requireNonNull(request.envId(), "env_id");
        return adapter.showEventDetail(request);
    }

    /**
     * 校验入参后委托 adapter 取回 clob 全文（T29）。
     *
     * @param request clob 请求，不能为 null；{@code businessId} 可为 null（adapter 回落配置）
     * @return clob 全文
     * @throws InvalidParamException {@code envId} 或 {@code clobId} 缺失时抛出
     */
    public ApmClobDetailResponse showClobDetail(ApmClobDetailRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        Validations.requireNonNull(request.envId(), "env_id");
        Validations.requireNonBlank(request.clobId(), "clob_id");
        return adapter.showClobDetail(request);
    }

    private void validateTraces(ApmQueryTracesRequest request) {
        if (request.page() == null || request.page() < PAGE_MIN) {
            throw new InvalidParamException("page must be >= " + PAGE_MIN);
        }
        if (request.pageSize() == null
                || request.pageSize() < PAGE_SIZE_MIN
                || request.pageSize() > PAGE_SIZE_MAX) {
            throw new InvalidParamException(
                    "page_size must be in [" + PAGE_SIZE_MIN + ", " + PAGE_SIZE_MAX + "]");
        }
        if (request.timeUsedMin() != null && request.timeUsedMin() < 0) {
            throw new InvalidParamException("time_used_min must be >= 0");
        }
    }
}
