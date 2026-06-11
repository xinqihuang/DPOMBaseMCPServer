/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code list_apm_business} 工具的响应 DTO，对应 SDK {@code ListBusinessResponse} 的无损投影（§4.1）。
 *
 * @param businessNodes 应用列表，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmListBusinessResponse(
        @JsonProperty("business_nodes") List<ApmBusinessNode> businessNodes) {
}
