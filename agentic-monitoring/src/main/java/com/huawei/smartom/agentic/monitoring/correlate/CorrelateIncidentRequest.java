/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.correlate;

/**
 * {@code correlate_incident} 工具的请求 DTO。
 *
 * <p>给定一个事故时间窗口与可选的资源标识，扇出查询 CES 告警 / AOM 日志 / APM trace，
 * 用于辅助 Agent 在一次调用内构建跨组件证据链。
 *
 * <p>所有过滤项都是可选的：缺省时对应分支跳过查询，结果项中的 {@code skipped=true}。
 *
 * @param startTimeMillis    时间窗起点（UTC 毫秒）
 * @param endTimeMillis      时间窗终点（UTC 毫秒），必须 {@code > startTimeMillis}
 * @param cesNamespace       CES 命名空间，用于过滤告警历史（可选）
 * @param logCategory        AOM 日志类型 {@code app_log/node_log/custom_log}（可选；为空则跳过日志查询）
 * @param logKeyword         AOM 日志关键字（可选）
 * @param apmTraceId         单个 trace id；提供时同时查询 trace 详情与拓扑（可选）
 * @param apmSource          APM 入口资源模糊匹配（可选；与 trace id 互补，二者均空时跳过 trace 查询）
 * @author h00884391
 * @since 2026-05-28
 */
public record CorrelateIncidentRequest(
        Long startTimeMillis,
        Long endTimeMillis,
        String cesNamespace,
        String logCategory,
        String logKeyword,
        String apmTraceId,
        String apmSource) {
}
