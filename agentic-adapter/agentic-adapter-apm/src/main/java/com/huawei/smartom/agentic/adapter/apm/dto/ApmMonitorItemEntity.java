/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * APM 单个监控项，对应 SDK v1 {@code MonitorItemEntity} 的无损投影
 * （T19 §4.1 准则，9 字段全保留）。
 *
 * <p>{@code collectorId} 在 SDK 中为 {@link Integer}（注意，{@code ShowMonitorItemViewConfig}
 * 请求中的同名字段是 {@link Long}）；T23 关键论点：{@code collectorId} 是 <em>env 局部</em>，
 * 同一数值在不同 env 含义不同，禁止跨 env 复用或硬编码。
 *
 * @param categoryId       类别 id
 * @param collectorName    采集器英文名
 * @param displayName      采集器展示名
 * @param showInTotal      是否在总览展示
 * @param monitorItemId    监控项 id
 * @param disabled         是否被禁用
 * @param collectorId      采集器 id（env 局部，禁止跨 env 复用）
 * @param sequence         展示序号
 * @param collectInterval  默认采集间隔（秒）
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmMonitorItemEntity(
        Integer categoryId,
        String collectorName,
        String displayName,
        Boolean showInTotal,
        Long monitorItemId,
        Boolean disabled,
        Integer collectorId,
        Integer sequence,
        Integer collectInterval) {
}
