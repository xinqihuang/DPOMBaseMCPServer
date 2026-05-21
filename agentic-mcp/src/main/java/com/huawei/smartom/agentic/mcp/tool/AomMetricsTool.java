package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.aom.AomMetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tool wrapping the {@code list_aom_metrics} capability.
 *
 * <p>Tool naming, description and parameter semantics follow
 * {@code docs/specs/tools/list_aom_metrics_v0.2.md}.
 */
@Component
public class AomMetricsTool {

    private static final Logger LOG = LoggerFactory.getLogger(AomMetricsTool.class);

    private final AomMetricsService service;

    public AomMetricsTool(AomMetricsService service) {
        this.service = service;
    }

    /**
     * Tool description text is mirrored from the spec; do not paraphrase casually —
     * the Agent's tool selection depends on its precision.
     */
    @Tool(
            name = "list_aom_metrics",
            description = """
                    List available AOM (Application Operations Management) metric definitions \
                    for an application, component, container, process, or other monitored object \
                    in Huawei Cloud. Use this BEFORE query_aom_metric_data to discover which \
                    metric names exist for a given namespace (PAAS.CONTAINER / PAAS.NODE / \
                    PAAS.SLA / PAAS.AGGR / CUSTOMMETRICS) or a specific inventoryId. AOM covers \
                    application-layer metrics; for cloud infrastructure metrics (ECS / RDS / EVS \
                    etc.) use list_ces_metrics instead. Returns metric metadata (namespace, \
                    metric_name, dimensions, unit) — NOT actual data points.""")
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
        try {
            return service.listMetrics(req);
        } catch (SmartomException e) {
            LOG.warn("list_aom_metrics failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
