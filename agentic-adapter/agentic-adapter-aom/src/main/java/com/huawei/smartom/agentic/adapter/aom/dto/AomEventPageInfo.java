/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code list_aom_events} 响应的分页元数据，对应 SDK {@code PageInfo}（marker 制）。
 *
 * <p>注意与 {@link AomPagination}（指标查询的 offset 制分页）不是同一 SDK 类型，不可混用。
 *
 * @param currentCount   当前页条目数
 * @param previousMarker 上一页标记，可为 {@code null}
 * @param nextMarker     下一页标记，翻页时回填请求的 {@code marker}；无后续页时可为 {@code null}
 * @author h00884391
 * @since 2026-06-11
 */
public record AomEventPageInfo(
        @JsonProperty("current_count") Integer currentCount,
        @JsonProperty("previous_marker") String previousMarker,
        @JsonProperty("next_marker") String nextMarker) {
}
