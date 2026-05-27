/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * 单个 CES 监控数据点。聚合统计字段（{@code max}、{@code min}、{@code average}、{@code sum}、
 * {@code variance}）按请求中指定的 {@code filter} 字段填充，未选择的统计项保持为 {@code null}。
 *
 * @param timestamp 采集时间戳，毫秒级 UNIX 时间
 * @param unit      指标单位，例如 {@code %}
 * @param max       周期内最大值，可能为 {@code null}
 * @param min       周期内最小值，可能为 {@code null}
 * @param average   周期内平均值，可能为 {@code null}
 * @param sum       周期内求和值，可能为 {@code null}
 * @param variance  周期内方差，可能为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record CesDatapoint(
        Long timestamp,
        String unit,
        Double max,
        Double min,
        Double average,
        Double sum,
        Double variance) {
}
