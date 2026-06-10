/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code show_apm_monitor_item_view_config} 工具的响应 DTO，对应 SDK
 * {@code ShowMonitorItemViewConfigResponse} 的无损投影（T19 §4.1 准则，4 字段全保留）。
 *
 * <p>Agent 在拿到此响应后，从 {@code viewRowList[*].viewList[*]} 中挑选一个 {@link ApmViewBase}
 * （含 {@code metric_set} / {@code field_item_list[*].function} 等真实字面量），原样拷贝
 * 进 {@code show_apm_trend} 的 {@code viewConfig} 入参——禁止凭模型先验编造任何字段。
 *
 * @param title         监控项标题
 * @param collectorName 采集器英文名
 * @param viewRowList   视图行列表（每行多视图）
 * @param style         展示风格字面量
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmMonitorItemViewConfigResponse(
        String title,
        String collectorName,
        List<ApmViewRow> viewRowList,
        String style) {
}
