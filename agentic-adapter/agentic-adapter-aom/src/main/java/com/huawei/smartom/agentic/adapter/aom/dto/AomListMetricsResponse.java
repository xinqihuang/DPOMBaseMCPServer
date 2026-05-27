/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 列出 AOM 指标的结果 DTO，包含单页指标定义列表以及分页元数据。
 *
 * @param metrics    当前页返回的指标定义列表
 * @param pagination 用于遍历完整结果集的分页元数据
 *
 * @author h00884391
 * @since 2026-05-21
 */
public record AomListMetricsResponse(
        @JsonProperty("metrics") List<AomMetricInfo> metrics,
        @JsonProperty("pagination") AomPagination pagination) {
}
