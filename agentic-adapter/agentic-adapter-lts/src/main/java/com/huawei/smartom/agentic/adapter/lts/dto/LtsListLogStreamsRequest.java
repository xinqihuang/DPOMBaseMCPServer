/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

/**
 * LTS 日志流发现请求。
 *
 * @param logGroupName 日志组名称过滤条件，可为空
 * @param logStreamName 日志流名称过滤条件，可为空
 * @author OpenAI
 * @since 2026-08-15
 */
public record LtsListLogStreamsRequest(String logGroupName, String logStreamName) {
}
