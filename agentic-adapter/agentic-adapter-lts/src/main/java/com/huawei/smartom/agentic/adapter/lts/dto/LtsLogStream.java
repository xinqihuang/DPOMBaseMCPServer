/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * LTS 日志流的无损投影。
 *
 * @param creationTime 创建时间
 * @param logStreamId 日志流 id
 * @param logStreamName 日志流名称
 * @param logStreamNameAlias 日志流别名
 * @param tag 标签
 * @param filterCount 过滤器数量
 * @param whetherLogStorage 是否存储日志
 * @param hotColdSeparation 是否开启冷热分离
 * @param authWebTracking 是否授权 Web Tracking
 * @param ttlInDays 保存天数
 * @param hotStorageDays 热存储天数
 * @param logGroupId 所属日志组 id
 * @param isFavorite 是否收藏
 * @author OpenAI
 * @since 2026-08-15
 */
public record LtsLogStream(
        @JsonProperty("creation_time") Long creationTime,
        @JsonProperty("log_stream_id") String logStreamId,
        @JsonProperty("log_stream_name") String logStreamName,
        @JsonProperty("log_stream_name_alias") String logStreamNameAlias,
        Map<String, String> tag,
        @JsonProperty("filter_count") Integer filterCount,
        @JsonProperty("whether_log_storage") Boolean whetherLogStorage,
        @JsonProperty("hot_cold_separation") Boolean hotColdSeparation,
        @JsonProperty("auth_web_tracking") Boolean authWebTracking,
        @JsonProperty("ttl_in_days") Integer ttlInDays,
        @JsonProperty("hot_storage_days") Integer hotStorageDays,
        @JsonProperty("log_group_id") String logGroupId,
        @JsonProperty("is_favorite") Boolean isFavorite) {
}
