/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LTS 日志组发现响应。
 *
 * @param logGroups 日志组列表
 * @author OpenAI
 * @since 2026-08-15
 */
public record LtsListLogGroupsResponse(
        @JsonProperty("log_groups") List<LtsLogGroup> logGroups) {
}
