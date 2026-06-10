/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * 趋势图视图配置，对应 SDK v1 {@code TrendView} 的无损投影
 * （T19 §4.1 准则，12 字段全保留）。
 *
 * <p>{@code viewType} 与 {@code tableDirection} 在 SDK 端是枚举（分别取值
 * {@code trend/sumtable/rawtable} 与 {@code H/V}），DTO 用 {@link String} 保持
 * 字面量，由 adapter 负责字符串↔SDK 枚举的双向映射。
 *
 * @param viewType        视图类型字面量（{@code trend} / {@code sumtable} / {@code rawtable}）
 * @param collectorName   采集器名称
 * @param metricSet       指标集名称
 * @param title           图表标题
 * @param tableDirection  表头方向字面量（{@code H} 横向 / {@code V} 纵向）
 * @param groupBy         分组字段
 * @param filter          过滤表达式
 * @param fieldItemList   字段配置数组；{@code null} 与空列表语义不同，调用方需如实传递
 * @param span            是否启用跨度
 * @param spanField       跨度字段属性
 * @param orderBy         排序表达式
 * @param latest          latest 表达式
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmTrendViewConfig(
        String viewType,
        String collectorName,
        String metricSet,
        String title,
        String tableDirection,
        String groupBy,
        String filter,
        List<ApmTrendFieldItem> fieldItemList,
        Boolean span,
        String spanField,
        String orderBy,
        String latest) {
}
