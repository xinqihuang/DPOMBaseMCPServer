/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.monitoring.aom.AomMetricsService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code list_aom_metrics} 能力的 MCP 工具。
 *
 * <p>工具命名、描述及参数语义遵循
 * {@code docs/specs/tools/list_aom_metrics_v0.2.md}。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class AomMetricsTool implements McpTool {

    private final AomMetricsService service;

    /**
     * 构造 {@code AomMetricsTool} 实例，将工具调用委托给底层监控服务。
     *
     * @param service AOM 监控编排服务，负责入参校验与 adapter 分发
     */
    public AomMetricsTool(AomMetricsService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：根据指定的 namespace 或 inventoryId 列出 AOM 指标定义。
     *
     * <p>{@link Tool} 注解中的描述文本与 spec 保持一致，请勿随意改写——
     * Agent 的工具选择依赖该描述的精确度。
     *
     * @param namespace   AOM 命名空间（预定义的 PAAS.* 取值、CUSTOMMETRICS 或自定义命名空间）；
     *                    未提供 {@code inventoryId} 时必填
     * @param metricName  指标名称精确过滤，可选
     * @param dimensions  维度过滤列表，多个维度按 AND 组合，可选
     * @param inventoryId 资源清单 ID（{@code resType_resId}）；未提供 {@code namespace} 时必填
     * @param limit       分页大小，取值范围 [1, 1000]，为 {@code null} 时由下游使用默认值
     * @param start       分页偏移（从 0 开始），为 {@code null} 时由下游使用默认值
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse}，
     *         捕获 {@link com.huawei.smartom.agentic.common.exception.SmartomException}
     *         时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "list_aom_metrics",
            description = """
                    Step 1 of the AOM query chain: list available AOM (Application Operations \
                    Management) metric definitions for an application, component, container, \
                    process, or other monitored object in Huawei Cloud. ALWAYS call this BEFORE \
                    query_aom_metric_data to discover the REAL metric names and dimension names \
                    for a given namespace (PAAS.CONTAINER / PAAS.NODE / PAAS.SLA / PAAS.AGGR / \
                    CUSTOMMETRICS) or a specific inventoryId — do not invent them. AOM covers \
                    application-layer metrics; for cloud infrastructure metrics (ECS / RDS / EVS \
                    etc.) use list_ces_metrics instead. Returns metric metadata (namespace, \
                    metric_name, dimensions, unit) — NOT actual data points; results are cached \
                    server-side for about 1 hour.""")
    public Object listAomMetrics(
            @ToolParam(
                    description = "AOM namespace. Predefined values: PAAS.CONTAINER / PAAS.NODE "
                            + "/ PAAS.SLA / PAAS.AGGR / CUSTOMMETRICS, or a custom namespace. "
                            + "Required unless inventory_id is provided.",
                    required = false)
            String namespace,
            @ToolParam(
                    description = "Exact metric name, e.g. aom_process_cpu_usage. Length 1-255.",
                    required = false)
            String metricName,
            @ToolParam(
                    description = "Dimension filter list. Each element: {name, value}. "
                            + "All dimensions are AND-combined. "
                            + "Example: [{\"name\":\"appName\",\"value\":\"order-svc\"}].",
                    required = false)
            List<AomMetricDimension> dimensions,
            @ToolParam(
                    description = "Resource inventory ID, format resType_resId. "
                            + "resType must be one of: host / application / instance / container "
                            + "/ process / network / storage / volume. "
                            + "Required unless namespace is provided.",
                    required = false)
            String inventoryId,
            @ToolParam(description = "Page size in [1, 1000], default 100.", required = false)
            Integer limit,
            @ToolParam(description = "Page offset (0-based), default 0.", required = false)
            Integer start) {

        AomListMetricsRequest req = new AomListMetricsRequest(
                namespace, metricName, dimensions, inventoryId, limit, start);
        return ToolCallSupport.execute("list_aom_metrics", () -> service.listMetrics(req));
    }
}
