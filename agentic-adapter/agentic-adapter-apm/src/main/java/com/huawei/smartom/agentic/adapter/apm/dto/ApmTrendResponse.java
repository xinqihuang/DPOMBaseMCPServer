/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * {@code show_apm_trend} 工具的响应 DTO，对应 SDK {@code ShowTrendResponse} 的无损投影。
 *
 * <p>Java 字段名 {@code latestDataTime} 经全局 Jackson SNAKE_CASE 输出为
 * {@code latest_data_time}（上游 SDK JSON key 为 {@code latest_data_Time}，含大写 T，
 * 本契约层不向外暴露该拼写瑕疵）。
 *
 * @param lineList       曲线列表，可能为空但不会为 {@code null}
 * @param latestDataTime 最新数据点时间戳（毫秒），可能为 {@code null}
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmTrendResponse(
        List<ApmTrendLine> lineList,
        Long latestDataTime) {
}
