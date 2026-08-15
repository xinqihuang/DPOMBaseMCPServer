/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code query_traces} 工具的响应 DTO。
 *
 * @param total 命中条数，可能为 {@code null}
 * @param spans span 摘要列表，可能为空但不会为 {@code null}
 * @param page 当前页码
 * @param pageSize 当前单页大小
 * @param hasMore 是否还有下一页；上游未返回 {@code total} 时为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmQueryTracesResponse(
        Integer total,
        List<ApmSpan> spans,
        Integer page,
        Integer pageSize,
        Boolean hasMore) {

    /**
     * 构造响应并冻结 span 列表，避免工具返回后被调用方修改。
     */
    public ApmQueryTracesResponse {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }

    /**
     * 保留旧的二参数构造方式，供不关心分页元数据的内部调用与测试使用。
     *
     * @param total 命中条数
     * @param spans span 摘要列表
     */
    public ApmQueryTracesResponse(Integer total, List<ApmSpan> spans) {
        this(total, spans, null, null, null);
    }
}
