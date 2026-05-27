/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.ces.CesMetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool wrapping the {@code list_ces_metrics} capability.
 *
 * <p>Tool naming, description and parameter semantics follow
 * {@code docs/specs/tools/list_ces_metrics.md}.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class CesMetricsTool {

    private static final Logger LOG = LoggerFactory.getLogger(CesMetricsTool.class);

    private final CesMetricsService service;

    /**
     * Constructs a new {@code CesMetricsTool} that delegates tool invocations to the
     * underlying monitoring service.
     *
     * @param service the CES monitoring orchestration service performing validation and adapter dispatch
     */
    public CesMetricsTool(CesMetricsService service) {
        this.service = service;
    }

    /**
     * MCP entry point: lists CES metric definitions for the given namespace, metric name, or dimension.
     *
     * <p>Tool description text on the {@link Tool} annotation is mirrored from the spec; do not
     * paraphrase casually — the Agent's tool selection depends on its precision.
     *
     * @param namespace  CES namespace such as {@code SYS.ECS}, optional
     * @param metricName exact metric name, e.g. {@code cpu_util}, optional
     * @param dimName    dimension name (e.g. {@code instance_id}); must be paired with {@code dimValue}
     * @param dimValue   dimension value; must be paired with {@code dimName}
     * @param limit      page size in [1, 1000], default 100 applied downstream when {@code null}
     * @param start      pagination marker returned by a previous response, optional
     * @param order      sort order, {@code "asc"} or {@code "desc"} (default {@code "desc"})
     * @return the {@link com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse} on success,
     *         or an {@link ErrorResponse} when a {@link SmartomException} is caught
     */
    @Tool(
            name = "list_ces_metrics",
            description = """
                    List available CES (Cloud Eye Service) metric definitions for Huawei Cloud \
                    resources. Use this to discover which metrics can be queried for a given \
                    namespace (e.g., SYS.ECS, SYS.RDS) or a specific resource. Returns metric \
                    metadata (name, namespace, dimensions, unit), not actual data points. \
                    Call query_metric_data afterwards to get values.""")
    public Object listCesMetrics(
            @ToolParam(description = "CES namespace like SYS.ECS, optional", required = false)
            String namespace,
            @ToolParam(description = "Exact metric name, e.g. cpu_util, optional", required = false)
            String metricName,
            @ToolParam(description = "Dimension name like instance_id; must accompany dim_value",
                       required = false)
            String dimName,
            @ToolParam(description = "Dimension value; must accompany dim_name", required = false)
            String dimValue,
            @ToolParam(description = "Page size in [1, 1000], default 100", required = false)
            Integer limit,
            @ToolParam(description = "Pagination marker from a previous response", required = false)
            String start,
            @ToolParam(description = "Sort order: 'asc' or 'desc', default 'desc'", required = false)
            String order) {

        CesListMetricsRequest req = new CesListMetricsRequest(
                namespace, metricName, dimName, dimValue, limit, start, order);
        try {
            return service.listMetrics(req);
        } catch (SmartomException e) {
            LOG.warn("list_ces_metrics failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
