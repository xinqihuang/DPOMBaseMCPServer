/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * {@code search_apm_application} 工具的响应 DTO，对应 SDK {@code SearchApplicationResponse}
 * 的无损投影（§4.1，3 字段全量）。
 *
 * <p>{@code appInfoMap}（组件名 → 详情）与 {@code appInfoList} 内容可能重叠，
 * 照 SDK 原样两者都保留，不做去重。
 *
 * @param appInfoList   组件/环境列表，可能为空但不会为 {@code null}
 * @param appTotalCount 组件总数
 * @param appInfoMap    组件名称到组件详情的映射，上游缺省时可为 {@code null}
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmSearchApplicationResponse(
        @JsonProperty("app_info_list") List<ApmAppInfo> appInfoList,
        @JsonProperty("app_total_count") Integer appTotalCount,
        @JsonProperty("app_info_map") Map<String, ApmAppInfo> appInfoMap) {
}
