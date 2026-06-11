/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchMetricQuery;
import com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataRequest;
import com.huawei.smartom.agentic.monitoring.ces.CesBatchMetricDataService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code batch_query_ces_metric_data} 能力的 MCP 工具。
 *
 * <p>用于在一次请求中批量查询多条 CES 指标在指定时间区间、聚合粒度下的数据点序列；
 * 适合跨资源关联分析或大盘渲染场景，能显著减少调用次数。查询项的 {@code namespace}
 * 以 {@link com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace} 受控枚举承载（T24），
 * Tool 层经 {@link CesBatchMetricQueryInput} 映射回 adapter 的 String 形态。
 *
 * @author h00884391
 * @since 2026-06-02
 */
@Component
public class CesBatchMetricDataTool {

    private final CesBatchMetricDataService service;

    /**
     * 构造 {@code CesBatchMetricDataTool} 实例。
     *
     * @param service CES 批量指标数据查询服务
     */
    public CesBatchMetricDataTool(CesBatchMetricDataService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按时间区间与聚合粒度批量查询多条 CES 指标的数据点。
     *
     * @param metrics 指标查询项列表，长度 [1, 500]，每项需含 namespace / metric_name / dimensions
     * @param filter  聚合方式
     * @param period  聚合粒度（秒）
     * @param from    起始时间（毫秒）
     * @param to      结束时间（毫秒）
     * @return 成功时返回
     *         {@link com.huawei.smartom.agentic.adapter.ces.dto.CesBatchQueryMetricDataResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "batch_query_ces_metric_data",
            description = """
                    Batch query monitoring data points for multiple CES (Cloud Eye Service) metrics \
                    in a single call. Up to 500 metric queries can be combined; each query specifies \
                    namespace (closed enum of 14 supported services), metric_name and dimensions, \
                    and they all share the same filter / period / from / to. Returns aggregated \
                    values (max / min / average / sum / variance) per period bucket for every \
                    metric, in the same order as requested. Prefer this over repeated \
                    query_ces_metric_data calls when fetching many metrics. Call order: \
                    list_ces_metrics -> this tool. metric_name and dimension NAMES MUST come from \
                    a prior list_ces_metrics response — do NOT invent them; dimension VALUES come \
                    from the alarm payload or resource info. For RDS always pass namespace SYS.RDS \
                    — the server detects cluster deployments automatically and reports the \
                    actually-queried namespace in each result's resolved_namespace field. \
                    'from'/'to' are UNIX timestamps in milliseconds; 'period' is the aggregation \
                    granularity in seconds (1 / 60 / 300 / 1200 / 3600 / 14400 / 86400).""")
    public Object batchQueryCesMetricData(
            @ToolParam(description = "Metric query items, 1-500. Each item: "
                    + "{\"namespace\":\"SYS.ECS\",\"metricName\":\"cpu_util\","
                    + "\"dimensions\":[{\"name\":\"instance_id\",\"value\":\"d911...\"}]}. "
                    + "namespace is a closed enum — see query_ces_metric_data for the 14 values.")
            List<CesBatchMetricQueryInput> metrics,
            @ToolParam(description = "Aggregation method: average / max / min / sum / variance.")
            String filter,
            @ToolParam(description = "Aggregation period in seconds: 1, 60, 300, 1200, 3600, "
                    + "14400, or 86400.")
            Integer period,
            @ToolParam(description = "Start time in UNIX millis (inclusive).")
            Long from,
            @ToolParam(description = "End time in UNIX millis (exclusive), must be > from.")
            Long to) {

        return ToolCallSupport.execute("batch_query_ces_metric_data", () -> {
            CesBatchQueryMetricDataRequest req = new CesBatchQueryMetricDataRequest(
                    toQueries(metrics),
                    ToolValidations.requireCesFilter(filter),
                    ToolValidations.requireCesPeriod(period),
                    from, to);
            return service.batchQueryMetricData(req);
        });
    }

    /**
     * 将工具入参查询项（枚举 namespace）映射为 adapter 形态（String namespace）。
     *
     * <p>{@code null} 列表与 {@code null} 元素原样透传，交由 service 层校验产生
     * {@code INVALID_PARAM}，保持既有错误语义不变。
     *
     * @param inputs 工具入参查询项，可为 {@code null}
     * @return adapter 形态的查询项列表
     */
    private static List<CesBatchMetricQuery> toQueries(List<CesBatchMetricQueryInput> inputs) {
        if (inputs == null) {
            return null;
        }
        return inputs.stream()
                .map(input -> input == null ? null : new CesBatchMetricQuery(
                        ToolValidations.cesNamespaceValue(input.namespace()),
                        input.metricName(),
                        input.dimensions()))
                .toList();
    }
}
