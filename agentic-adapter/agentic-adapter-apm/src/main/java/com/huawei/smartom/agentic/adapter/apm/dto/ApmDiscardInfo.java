/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 调用链事件的丢弃统计，对应 SDK {@code DiscardInfo} 的无损投影（§4.1，3 字段全量）。
 *
 * <p>注意 {@code totalTime} 是 SDK 原样的驼峰 JSON 键，照抄不规范化。
 *
 * @param type      丢弃类型
 * @param count     丢弃数量
 * @param totalTime 累计耗时（毫秒，JSON 键 {@code totalTime}，SDK 原样）
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmDiscardInfo(
        @JsonProperty("type") String type,
        @JsonProperty("count") Integer count,
        @JsonProperty("totalTime") Long totalTime) {
}
