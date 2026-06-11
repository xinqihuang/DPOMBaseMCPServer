/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * {@code list_aom_events} 工具的请求 DTO，对应华为云 AOM v2 {@code ListEvents}
 * 接口（POST /v2/{project_id}/events）的入参。
 *
 * <p>{@code type}/{@code limit}/{@code marker} 走 query 参数，其余字段进请求体
 * {@code EventQueryParam2}。{@code Enterprise-Project-Id} header 不透传，
 * 理由见 spec {@code docs/specs/tools/list_aom_events.md} §2。
 *
 * @param type             查询类型（活动/历史告警），为 {@code null} 时返回全部事件告警
 * @param timeRange        查询时间范围 {@code startMs.endMs.durationMin}，必填，
 *                         格式同 {@code query_aom_metric_data}
 * @param step             统计步长（毫秒），可选，必须大于 0
 * @param search           metadata 模糊匹配关键字，可选
 * @param sortOrderBy      排序字段列表；提供 {@code sortOrder} 时必填
 * @param sortOrder        排序方向 {@code asc} / {@code desc}，可选
 * @param metadataRelation metadata 过滤条件列表，可选
 * @param limit            单页条数，可选（上游默认 1000），必须不小于 1
 * @param marker           分页标记，初始 {@code "0"}，后续取响应 {@code page_info.next_marker}
 * @author h00884391
 * @since 2026-06-11
 */
public record AomListEventsRequest(
        AomAlertType type,
        String timeRange,
        Long step,
        String search,
        List<String> sortOrderBy,
        String sortOrder,
        List<AomEventMetadataRelation> metadataRelation,
        Integer limit,
        String marker) {
}
