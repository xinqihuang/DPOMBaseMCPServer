/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * 趋势图单条曲线，对应 SDK v1 {@code FrontLine} 的无损投影
 * （T19 §4.1 准则，6 字段全保留）。
 *
 * @param pointList 数据点列表
 * @param title     曲线标题
 * @param unit      数据单位
 * @param precision 精度（百分比小数位）
 * @param dataType  数据类型字面量（如 {@code double} / {@code long}）
 * @param visible   是否可见
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmTrendLine(
        List<ApmTrendPoint> pointList,
        String title,
        String unit,
        Integer precision,
        String dataType,
        Boolean visible) {
}
