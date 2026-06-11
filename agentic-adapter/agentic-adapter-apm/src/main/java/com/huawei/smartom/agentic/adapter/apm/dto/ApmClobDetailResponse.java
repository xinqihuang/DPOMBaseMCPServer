/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code show_clob_detail} 工具的响应 DTO，对应 SDK {@code ShowClobDetailResponse}
 * 的无损投影（§4.1，1 字段全量）。
 *
 * @param clobString 超长字段全文（完整异常堆栈 / 完整 SQL 等）
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmClobDetailResponse(
        @JsonProperty("clob_string") String clobString) {
}
