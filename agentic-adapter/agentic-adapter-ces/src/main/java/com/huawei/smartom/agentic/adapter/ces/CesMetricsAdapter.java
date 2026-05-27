/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesQueryMetricDataResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 CES 监控查询适配器。
 *
 * <p>实现类内部完成限流、重试以及异常映射，失败时仅向上抛出 {@link SmartomException} 体系中的异常。
 *
 * @author h00884391
 * @since 2026-05-21
 */
public interface CesMetricsAdapter {

    /**
     * 按给定过滤条件查询 CES 监控指标定义。
     *
     * @param request 过滤与分页请求（不能为 null）
     * @return 匹配的指标定义列表及分页元数据
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesListMetricsResponse listMetrics(CesListMetricsRequest request);

    /**
     * 按时间区间与聚合粒度查询单条 CES 指标的监控数据点。
     *
     * @param request 查询请求（不能为 null），含 namespace / metric / dim / filter / period / from / to
     * @return 指标的数据点列表（按时间升序）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesQueryMetricDataResponse queryMetricData(CesQueryMetricDataRequest request);

    /**
     * 按条件查询 CES 告警历史。
     *
     * @param request 告警历史查询请求（不能为 null）
     * @return 告警历史摘要列表及总数
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesListAlarmsResponse listAlarms(CesListAlarmsRequest request);
}
