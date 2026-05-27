/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.correlate;

/**
 * {@code correlate_incident} 工具的响应 DTO，聚合各分支结果。
 *
 * @param cesAlarms   CES 告警分支结果
 * @param aomLogs     AOM 日志分支结果
 * @param apmTraces   APM trace 搜索分支结果
 * @param apmTopology APM 拓扑分支结果（仅在提供 traceId 时不会被 skipped）
 * @author h00884391
 * @since 2026-05-28
 */
public record CorrelateIncidentResponse(
        CorrelateBranchResult cesAlarms,
        CorrelateBranchResult aomLogs,
        CorrelateBranchResult apmTraces,
        CorrelateBranchResult apmTopology) {
}
