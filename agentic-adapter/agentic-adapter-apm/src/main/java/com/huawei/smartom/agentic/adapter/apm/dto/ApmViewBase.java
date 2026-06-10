/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * APM 视图基础模型，对应 SDK v1 {@code ViewBase} 的无损投影
 * （T19 §4.1 准则，12 字段全保留）。
 *
 * <p>本 DTO 出现在 {@code show_apm_monitor_item_view_config} 的响应里——Agent 据此挑选某个
 * view 后，把其字段原样拷贝进 {@code show_apm_trend} 的 {@code viewConfig}（即
 * {@link ApmTrendViewConfig}）入参。
 *
 * <p>注意 SDK 端类型差异（不可凭直觉假定二者完全同构）：
 * <ul>
 *   <li>{@code ViewBase.latest} 是 {@link Boolean}；{@code TrendView.latest} 是 {@link String}。
 *       Agent 在转传时需要做类型转换（{@code true → "true"} 等）。</li>
 *   <li>其余 11 字段类型一致。</li>
 * </ul>
 *
 * @param collectorName  采集器英文名
 * @param metricSet      指标集名称
 * @param title          图表标题
 * @param tableDirection 表头方向字面量（{@code H} / {@code V}）
 * @param groupBy        分组表达式
 * @param filter         过滤表达式
 * @param fieldItemList  字段配置数组（含 {@code function} / {@code as}）
 * @param span           是否启用跨度
 * @param spanField      跨度字段属性
 * @param orderBy        排序表达式
 * @param latest         是否取最新值（Boolean，注意与 {@link ApmTrendViewConfig#latest()} 类型不同）
 * @param viewType       视图类型字面量（{@code trend} / {@code sumtable} / {@code rawtable}）
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmViewBase(
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
        Boolean latest,
        String viewType) {
}
