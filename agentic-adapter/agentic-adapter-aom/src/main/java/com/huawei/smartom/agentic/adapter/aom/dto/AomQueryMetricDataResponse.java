/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * {@code query_aom_metric_data} 工具的响应 DTO。
 *
 * @param series 时序结果列表，可能为空但不会为 {@code null}
 * @author h00884391
 * @since 2026-05-28
 */
public record AomQueryMetricDataResponse(List<AomSampleSeries> series) {
}
