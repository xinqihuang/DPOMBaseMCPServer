/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListMetricsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 用于查询 AOM 数据的端口接口。
 *
 * <p>实现类负责封装华为云 AOM SDK，并将 SDK 类型转换为本项目自定义的 DTO。
 * SDK 异常必须在离开实现之前被映射为 {@link SmartomException}。
 *
 * @author h00884391
 * @since 2026-05-21
 */
public interface AomMetricsAdapter {

    /**
     * 列出与给定请求匹配的 AOM 指标定义。
     *
     * @param request 查询参数，不能为 null
     * @return 单页指标定义列表，附带分页元数据
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    AomListMetricsResponse listMetrics(AomListMetricsRequest request);

    /**
     * 查询 AOM 单条时序的监控数据点（{@code ListSample}）。
     *
     * @param request 查询参数，不能为 null
     * @return 时序数据点结果
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    AomQueryMetricDataResponse queryMetricData(AomQueryMetricDataRequest request);

    /**
     * 查询 AOM 应用/主机/自定义日志（{@code ListLogItems}，{@code type=querylogs}）。
     *
     * @param request 查询参数，不能为 null
     * @return 上游 result 原始字符串响应
     * @throws SmartomException 当 SDK 或上游发生错误时抛出
     */
    AomQueryLogsResponse queryLogs(AomQueryLogsRequest request);
}
