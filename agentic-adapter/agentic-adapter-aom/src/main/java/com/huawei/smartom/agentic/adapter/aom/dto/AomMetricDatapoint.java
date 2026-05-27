/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * 单个 AOM 时序数据点。
 *
 * @param timestamp  采集时间戳，毫秒级 UNIX 时间
 * @param unit       指标单位，例如 {@code Percent}
 * @param statistics 各统计方式的结果，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record AomMetricDatapoint(
        Long timestamp,
        String unit,
        List<AomStatisticValue> statistics) {
}
