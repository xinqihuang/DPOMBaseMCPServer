/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AOM 指标查询返回的分页元数据。
 *
 * <p>注意：{@code nextToken} 是 {@link Integer} 类型的偏移量，与 CES 使用字符串 marker 的方式不同。
 *
 * @param count     当前页的条目数量
 * @param total     在已知的情况下，所有分页的总条目数
 * @param offset    当前页在完整结果集中的零起始偏移，可能为 {@code null}
 * @param nextToken 下一页的偏移标记；无后续页时为 {@code null}
 * @param hasMore   当存在后续页时为 {@code true}
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomPagination(
        @JsonProperty("count") int count,
        @JsonProperty("total") int total,
        @JsonProperty("offset") Integer offset,
        @JsonProperty("next_token") Integer nextToken,
        @JsonProperty("has_more") boolean hasMore) {
}
