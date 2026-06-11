/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomAlertType;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEventMetadataRelation;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.monitoring.aom.AomEventService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 {@code list_aom_events} 能力的 MCP 工具：查询 AOM 事件与告警（活动 / 历史 / 全部）。
 *
 * <p>工具命名、描述及参数语义遵循 {@code docs/specs/tools/list_aom_events.md}；
 * {@code type} 以 {@link AomAlertType} 受控枚举承载（CLAUDE.md §4.3 (a)）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class AomEventTool {

    private final AomEventService service;

    /**
     * 构造 {@code AomEventTool} 实例。
     *
     * @param service AOM 事件/告警查询服务
     */
    public AomEventTool(AomEventService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按时间范围与过滤条件查询 AOM 事件/告警。
     *
     * @param type             查询类型（受控枚举），可选；不传返回全部事件告警
     * @param timeRange        查询时间范围 {@code startMs.endMs.durationMin}
     * @param step             统计步长（毫秒），可选
     * @param search           metadata 模糊匹配关键字，可选
     * @param sortOrderBy      排序字段列表，提供 {@code sortOrder} 时必填
     * @param sortOrder        排序方向 {@code asc}/{@code desc}，可选
     * @param metadataRelation metadata 过滤条件列表，可选
     * @param limit            单页条数，可选（上游默认 1000）
     * @param marker           分页标记，可选
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "list_aom_events",
            description = """
                    List AOM (Application Operations Management) events and alarms in Huawei \
                    Cloud over a time window. Use type=active_alert for currently firing alarms, \
                    type=history_alert for resolved/expired ones, or omit type for all events \
                    and alarms. Each returned event carries a metadata map (severity, resource \
                    identifiers, etc.) — use those values to drill down with list_aom_metrics / \
                    query_aom_metric_data or query_logs. 'time_range' format is \
                    'startMillis.endMillis.durationMinutes' with -1 for server-side calculation \
                    (e.g. '-1.-1.60' = last 60 min). Paginate with 'marker' from the previous \
                    response's page_info.next_marker.""")
    public Object listAomEvents(
            @ToolParam(description = "Query type (closed enum): active_alert = firing alarms, "
                    + "history_alert = resolved alarms. Omit for all events and alarms.",
                    required = false)
            AomAlertType type,
            @ToolParam(description = "Time range: 'startMs.endMs.durationMin', e.g. '-1.-1.60' "
                    + "= last 60 min.")
            String timeRange,
            @ToolParam(description = "Statistic step in millis, e.g. 60000 for one minute.",
                    required = false)
            Long step,
            @ToolParam(description = "Fuzzy keyword matched against event metadata fields.",
                    required = false)
            String search,
            @ToolParam(description = "Sort field list; required when sort_order is provided.",
                    required = false)
            List<String> sortOrderBy,
            @ToolParam(description = "Sort direction: 'asc' or 'desc'.", required = false)
            String sortOrder,
            @ToolParam(description = "Metadata filters. Each item: {key, value[], relation}; "
                    + "relation is one of AND / OR / NOT. Keys come from the metadata map of "
                    + "previously returned events — do not invent them.",
                    required = false)
            List<AomEventMetadataRelation> metadataRelation,
            @ToolParam(description = "Page size; upstream default 1000.", required = false)
            Integer limit,
            @ToolParam(description = "Pagination marker: '0' initially, then "
                    + "page_info.next_marker from the previous response.", required = false)
            String marker) {

        AomListEventsRequest req = new AomListEventsRequest(
                type, timeRange, step, search, sortOrderBy, sortOrder,
                metadataRelation, limit, marker);
        return ToolCallSupport.execute("list_aom_events", () -> service.listEvents(req));
    }
}
