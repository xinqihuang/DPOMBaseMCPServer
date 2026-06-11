/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomFillValue;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricDimension;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricPeriod;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricStatistic;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.monitoring.aom.AomMetricDataService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code query_aom_metric_data} 能力的 MCP 工具。
 *
 * <p>T26 起 {@code statistics} / {@code period} / {@code fill_value} 以受控枚举承载
 * （CLAUDE.md §4.3 (a)），取值经 Spring AI 反射进工具 JSON Schema。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Component
public class AomMetricDataTool {

    private final AomMetricDataService service;

    /**
     * 构造 {@code AomMetricDataTool} 实例。
     *
     * @param service AOM 时序数据查询服务
     */
    public AomMetricDataTool(AomMetricDataService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：查询单条 AOM 时序在指定 {@code time_range} 与采样粒度下的数据点。
     *
     * @param namespace  AOM 命名空间
     * @param metricName 时间序列名，取自 {@code list_aom_metrics} 返回
     * @param dimensions 维度过滤列表，可选
     * @param statistics 统计方式列表（受控枚举），可选
     * @param period     采样粒度（受控枚举）
     * @param timeRange  时间区间字符串，例如 {@code -1.-1.60}（最近 60 分钟）
     * @param fillValue  断点插值策略（受控枚举），可选
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "query_aom_metric_data",
            description = """
                    Query AOM (Application Operations Management) sample series data over a time \
                    window for a Huawei Cloud application/container/process/node metric. Returns \
                    per-period datapoints with selected aggregations. Call order: \
                    list_aom_metrics -> this tool. metric_name and dimension NAMES MUST come \
                    from a prior list_aom_metrics response — do NOT invent them. 'period' is the \
                    sampling granularity in seconds (60 / 300 / 900 / 3600). 'time_range' format \
                    is 'startMillis.endMillis.durationMinutes' — use -1 for either start or end \
                    to let the server compute it (e.g. '-1.-1.60' = last 60 min).""")
    public Object queryAomMetricData(
            @ToolParam(description = "AOM namespace, e.g. PAAS.CONTAINER / PAAS.NODE / "
                    + "CUSTOMMETRICS, or a custom namespace.")
            String namespace,
            @ToolParam(description = "Time series name from a prior list_aom_metrics response, "
                    + "e.g. cpuUsage. Do not invent.")
            String metricName,
            @ToolParam(description = "Dimension filter list, each {name, value}. Dimension "
                    + "names come from list_aom_metrics.",
                    required = false)
            List<AomMetricDimension> dimensions,
            @ToolParam(description = "Statistic list (closed enum): maximum / minimum / sum / "
                    + "average / sampleCount.",
                    required = false)
            List<AomMetricStatistic> statistics,
            @ToolParam(description = "Sampling period in seconds (closed enum): 60 / 300 / 900 "
                    + "/ 3600.")
            AomMetricPeriod period,
            @ToolParam(description = "Time range: 'startMs.endMs.durationMin', "
                    + "e.g. '-1.-1.60' = last 60 min.")
            String timeRange,
            @ToolParam(description = "Fill strategy for missing points (closed enum): -1 / 0 / "
                    + "null / average.",
                    required = false)
            AomFillValue fillValue) {

        AomQueryMetricDataRequest req = new AomQueryMetricDataRequest(
                namespace, metricName, dimensions, statistics, period, timeRange, fillValue);
        return ToolCallSupport.execute("query_aom_metric_data", () -> service.queryMetricData(req));
    }
}
