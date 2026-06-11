/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace;

import java.util.List;

/**
 * {@code batch_query_ces_metric_data} 工具入参中单个指标查询项的 Schema 形态（T24）。
 *
 * <p>与 adapter 层的 {@code CesBatchMetricQuery} 的唯一差别是 {@code namespace} 以
 * {@link CesNamespace} 受控枚举承载——枚举常量经 Spring AI 反射进工具 JSON Schema，
 * 使 Agent 只能从合法取值中选择。Tool 层将其映射回 adapter 的 String 形态
 * （{@code SYS.RDS_MYSQL_CLUSTER} 等内部命名空间不在枚举内，adapter 边界必须保留
 * String 才能承载 service 层 fallback 的路由结果）。
 *
 * @param namespace  CES 命名空间（受控枚举，14 个受支持服务）
 * @param metricName 指标名，取自 {@code list_ces_metrics} 的返回
 * @param dimensions 维度列表，长度 [1, 4]
 * @author h00884391
 * @since 2026-06-11
 */
public record CesBatchMetricQueryInput(
        CesNamespace namespace,
        String metricName,
        List<CesMetricDimension> dimensions) {
}
