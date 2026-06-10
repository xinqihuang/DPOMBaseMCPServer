/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 APM 趋势图查询适配器。
 *
 * <p>与 {@link ApmAlarmAdapter} / {@link ApmTraceAdapter} 同模块、并列；trend 是
 * 独立于告警 / trace / 拓扑的业务域，按单一职责拆分接口，共享同一 {@code ApmClient} Bean。
 *
 * @author h00884391
 * @since 2026-06-10
 */
public interface ApmTrendAdapter {

    /**
     * 按 {@code monitor_item × view_config × 时间窗} 拉取趋势图原始数据。
     *
     * <p>对应 SDK {@code ApmClient.showTrend(ShowTrendRequest)} ——
     * {@code POST /v1/apm2/openapi/view/trend/show}。请求需要 {@code x-business-id} 头；
     * 若 {@link ApmTrendRequest#businessId()} 为 {@code null} 则回落到
     * {@code huaweicloud.apm-business-id} 配置项。
     *
     * @param request 查询请求（不能为 {@code null}）
     * @return 曲线列表 + 最新数据点时间（无损覆盖 SDK 全部响应字段）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ApmTrendResponse showTrend(ApmTrendRequest request);
}
