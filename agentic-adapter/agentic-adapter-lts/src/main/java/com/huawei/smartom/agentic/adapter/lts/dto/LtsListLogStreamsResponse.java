/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LTS 日志流发现响应。
 *
 * @param logStreams 日志流列表
 * @author OpenAI
 * @since 2026-08-15
 */
public record LtsListLogStreamsResponse(
        @JsonProperty("log_streams") List<LtsLogStream> logStreams) {
}
