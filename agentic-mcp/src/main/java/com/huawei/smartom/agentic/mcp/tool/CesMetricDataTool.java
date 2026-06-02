/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.ces.CesMetricDataService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code query_ces_metric_data} 能力的 MCP 工具。
 *
 * <p>用于查询华为云 CES 单条指标在指定时间区间、聚合粒度下的数据点序列；
 * 调用前通常先调用 {@code list_ces_metrics} 发现指标定义与维度。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CesMetricDataTool {

    private static final Logger LOG = LoggerFactory.getLogger(CesMetricDataTool.class);

    private final CesMetricDataService service;

    /**
     * 构造 {@code CesMetricDataTool} 实例。
     *
     * @param service CES 指标数据查询服务
     */
    public CesMetricDataTool(CesMetricDataService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按时间区间与聚合粒度查询单条 CES 指标的数据点。
     *
     * @param namespace  CES 命名空间，例如 {@code SYS.ECS}
     * @param metricName 指标名，例如 {@code cpu_util}
     * @param dimensions 维度列表，长度 [1, 4]
     * @param filter     聚合方式
     * @param period     聚合粒度（秒）
     * @param from       起始时间（毫秒）
     * @param to         结束时间（毫秒）
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse}，
     *         失败时返回 {@link ErrorResponse}
     */
    @Tool(
            name = "query_ces_metric_data",
            description = """
                    Query monitoring data points of a single CES (Cloud Eye Service) metric for a \
                    given Huawei Cloud resource over a time range. Returns aggregated values \
                    (max / min / average / sum / variance) per period bucket. Call \
                    list_ces_metrics first to discover available metric names and dimensions. \
                    'from'/'to' are UNIX timestamps in milliseconds; 'period' is the aggregation \
                    granularity in seconds (1 / 60 / 300 / 1200 / 3600 / 14400 / 86400).""")
    public Object queryCesMetricData(
            @ToolParam(description = "CES namespace like SYS.ECS or SYS.RDS.")
            String namespace,
            @ToolParam(description = "Exact metric name, e.g. cpu_util.")
            String metricName,
            @ToolParam(description = "Dimension list, 1-4 items, each {name,value}. "
                    + "Example: [{\"name\":\"instance_id\",\"value\":\"d911...\"}].")
            List<CesMetricDimension> dimensions,
            @ToolParam(description = "Aggregation method: average / max / min / sum / variance.")
            String filter,
            @ToolParam(description = "Aggregation period in seconds: 1, 60, 300, 1200, 3600, "
                    + "14400, or 86400.")
            Integer period,
            @ToolParam(description = "Start time in UNIX millis (inclusive).")
            Long from,
            @ToolParam(description = "End time in UNIX millis (exclusive), must be > from.")
            Long to) {

        try {
            CesQueryMetricDataRequest req = new CesQueryMetricDataRequest(
                    namespace, metricName, dimensions,
                    ToolValidations.requireCesFilter(filter),
                    ToolValidations.requireCesPeriod(period),
                    from, to);
            return service.queryMetricData(req);
        }
        catch (SmartomException e) {
            LOG.warn("query_ces_metric_data failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
