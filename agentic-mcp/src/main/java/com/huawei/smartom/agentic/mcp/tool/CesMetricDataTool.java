/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.monitoring.ces.CesMetricDataService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code query_ces_metric_data} 能力的 MCP 工具。
 *
 * <p>用于查询华为云 CES 单条指标在指定时间区间、聚合粒度下的数据点序列；
 * 调用前必须先调用 {@code list_ces_metrics} 发现指标定义与维度（T24，CLAUDE.md §4.3 (b)）。
 * {@code namespace} 以 {@link CesNamespace} 受控枚举承载。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class CesMetricDataTool {

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
     * @param namespace  CES 命名空间（受控枚举）
     * @param metricName 指标名，取自 {@code list_ces_metrics} 返回
     * @param dimensions 维度列表，长度 [1, 4]
     * @param filter     聚合方式
     * @param period     聚合粒度（秒）
     * @param from       起始时间（毫秒）
     * @param to         结束时间（毫秒）
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "query_ces_metric_data",
            description = """
                    Query monitoring data points of a single CES (Cloud Eye Service) metric for a \
                    given Huawei Cloud resource over a time range. Returns aggregated values \
                    (max / min / average / sum / variance) per period bucket. Call order: \
                    list_ces_metrics -> this tool. metric_name and dimension NAMES MUST come \
                    from a prior list_ces_metrics response — do NOT invent them; dimension \
                    VALUES (e.g. the concrete instance_id) come from the alarm payload or \
                    resource info. RDS splits namespaces by deployment form: SYS.RDS for \
                    primary/standby or single instances, SYS.RDS_MYSQL_CLUSTER for the MySQL \
                    cluster edition — when unsure, probe with list_ces_metrics (cached) and use \
                    the namespace that returns metric definitions for the instance. 'from'/'to' \
                    are UNIX timestamps in milliseconds; 'period' is the aggregation granularity \
                    in seconds (1 / 60 / 300 / 1200 / 3600 / 14400 / 86400).""")
    public Object queryCesMetricData(
            @ToolParam(description = "CES namespace (closed enum). One of: SYS.ECS (ECS), "
                    + "SYS.OBS (OBS), SYS.EVS (EVS disk), SYS.VPC (VPC/EIP), SYS.GEIP "
                    + "(Global EIP), SYS.DMS (DMS), SYS.DCS (DCS Redis), SYS.WAF (WAF), "
                    + "SYS.CFW (CFW), SYS.APIG (APIG shared), SYS.RDS (RDS primary/standby), "
                    + "SYS.RDS_MYSQL_CLUSTER (RDS MySQL cluster), SYS.ELB (ELB), SYS.DNS (DNS), "
                    + "SYS.NAT (NAT).")
            CesNamespace namespace,
            @ToolParam(description = "Exact metric name from a prior list_ces_metrics response, "
                    + "e.g. cpu_util. Do not invent.")
            String metricName,
            @ToolParam(description = "Dimension list, 1-4 items, each {name,value}. Dimension "
                    + "names come from list_ces_metrics; values from the alarm payload. "
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

        return ToolCallSupport.execute("query_ces_metric_data", () -> {
            CesQueryMetricDataRequest req = new CesQueryMetricDataRequest(
                    ToolValidations.cesNamespaceValue(namespace), metricName, dimensions,
                    ToolValidations.requireCesFilter(filter),
                    ToolValidations.requireCesPeriod(period),
                    from, to);
            return service.queryMetricData(req);
        });
    }
}
