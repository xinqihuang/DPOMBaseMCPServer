/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 趋势图视图所需展示的字段配置，对应 SDK v1 {@code FieldItem} 的无损投影
 * （T19 §4.1 准则，7 字段全保留）。
 *
 * @param function     字段表达式（如 {@code avg(latency)}）
 * @param as           字段别名
 * @param defaultValue 默认值
 * @param trace        是否参与 trace
 * @param precision    精度（百分比小数位）
 * @param unit         单位
 * @param visible      是否可见
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmTrendFieldItem(
        String function,
        String as,
        String defaultValue,
        Boolean trace,
        Integer precision,
        String unit,
        Boolean visible) {
}
