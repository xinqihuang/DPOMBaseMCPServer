/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace;
import com.huawei.smartom.agentic.monitoring.ces.CesMetricsService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code list_ces_metrics} 能力的 MCP 工具。
 *
 * <p>工具命名、描述及参数语义遵循
 * {@code docs/specs/tools/list_ces_metrics.md}；T24 起 {@code namespace}
 * 以 {@link CesNamespace} 受控枚举承载（CLAUDE.md §4.3 (a)）。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class CesMetricsTool {

    private final CesMetricsService service;

    /**
     * 构造 {@code CesMetricsTool} 实例，将工具调用委托给底层监控服务。
     *
     * @param service CES 监控编排服务，负责入参校验与 adapter 分发
     */
    public CesMetricsTool(CesMetricsService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：根据指定的命名空间、指标名称或维度列出 CES 指标定义。
     *
     * <p>{@link Tool} 注解中的描述文本与 spec 保持一致，请勿随意改写——
     * Agent 的工具选择依赖该描述的精确度。
     *
     * @param namespace  CES 命名空间（受控枚举），可选
     * @param metricName 指标名称精确匹配，如 {@code cpu_util}，可选
     * @param dimName    维度名称（如 {@code instance_id}），必须与 {@code dimValue} 成对出现
     * @param dimValue   维度取值，必须与 {@code dimName} 成对出现
     * @param limit      分页大小，取值范围 [1, 1000]，为 {@code null} 时由下游使用默认值 100
     * @param start      上一次响应返回的分页标记，可选
     * @param order      排序方式，{@code "asc"} 或 {@code "desc"}（默认 {@code "desc"}）
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse}，
     *         捕获 {@link com.huawei.smartom.agentic.common.exception.SmartomException}
     *         时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "list_ces_metrics",
            description = """
                    Step 1 of the CES query chain: list available CES (Cloud Eye Service) metric \
                    definitions for Huawei Cloud resources. ALWAYS call this BEFORE \
                    query_ces_metric_data / batch_query_ces_metric_data to discover the REAL \
                    metric_name and dimension NAMES under a namespace or for a specific resource \
                    — do NOT invent them from prior knowledge. The namespace parameter is a \
                    closed enum of 15 supported values. Returns metric metadata (name, \
                    namespace, dimensions, unit), not actual data points; results are cached \
                    server-side for about 1 day.""")
    public Object listCesMetrics(
            @ToolParam(description = "CES namespace (closed enum). One of: SYS.ECS (ECS), "
                    + "SYS.OBS (OBS), SYS.EVS (EVS disk), SYS.VPC (VPC/EIP), SYS.GEIP "
                    + "(Global EIP), SYS.DMS (DMS), SYS.DCS (DCS Redis), SYS.WAF (WAF), "
                    + "SYS.CFW (CFW), SYS.APIG (APIG shared), SYS.RDS (RDS primary/standby), "
                    + "SYS.RDS_MYSQL_CLUSTER (RDS MySQL cluster), SYS.ELB (ELB), SYS.DNS (DNS), "
                    + "SYS.NAT (NAT). Optional.", required = false)
            CesNamespace namespace,
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
                ToolValidations.cesNamespaceValue(namespace),
                metricName, dimName, dimValue, limit, start, order);
        return ToolCallSupport.execute("list_ces_metrics", () -> service.listMetrics(req));
    }
}
