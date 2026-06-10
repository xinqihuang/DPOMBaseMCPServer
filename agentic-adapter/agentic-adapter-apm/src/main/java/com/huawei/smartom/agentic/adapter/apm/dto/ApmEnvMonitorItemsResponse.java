/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code show_env_monitor_items} 工具的响应 DTO，对应 SDK
 * {@code ShowEnvMonitorItemsResponse} 的无损投影。
 *
 * <p>承担 T23 设计中「{@code monitor_item} ↔ {@code collector_id} 的 env 级映射表」职责：
 * Agent 在收到告警的 {@code monitor_item_id} 后，先查本响应解析出对应的 {@code collector_id}
 * 与 {@code collector_name}，再调用 {@code show_apm_monitor_item_view_config}。
 *
 * @param categoryInfoList    采集器类别集合
 * @param monitorItemInfoList 该 env 下的监控项集合
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmEnvMonitorItemsResponse(
        List<ApmCollectorCategoryInfo> categoryInfoList,
        List<ApmMonitorItemEntity> monitorItemInfoList) {
}
