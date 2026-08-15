/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * LTS 日志组的无损投影。
 *
 * @param creationTime 创建时间
 * @param logGroupName 日志组名称
 * @param logGroupId 日志组 id
 * @param ttlInDays 保存天数
 * @param tag 标签
 * @param logGroupNameAlias 日志组别名
 * @author OpenAI
 * @since 2026-08-15
 */
public record LtsLogGroup(
        @JsonProperty("creation_time") Long creationTime,
        @JsonProperty("log_group_name") String logGroupName,
        @JsonProperty("log_group_id") String logGroupId,
        @JsonProperty("ttl_in_days") Integer ttlInDays,
        Map<String, String> tag,
        @JsonProperty("log_group_name_alias") String logGroupNameAlias) {
}
