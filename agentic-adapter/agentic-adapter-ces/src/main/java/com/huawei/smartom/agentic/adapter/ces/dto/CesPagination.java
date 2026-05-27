/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CES 列表类响应的分页元数据。
 *
 * @param count       当前页的条目数
 * @param total       总条目数
 * @param nextMarker  下一页的不透明分页标记，无更多数据时为 null
 * @param hasMore     便捷标志，仅当还有下一页时为 true
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record CesPagination(
        @JsonProperty("count") int count,
        @JsonProperty("total") int total,
        @JsonProperty("next_marker") String nextMarker,
        @JsonProperty("has_more") boolean hasMore) {
}
