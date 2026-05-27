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
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmQueryTracesResponse(
        Integer total,
        List<ApmSpan> spans) {
}
