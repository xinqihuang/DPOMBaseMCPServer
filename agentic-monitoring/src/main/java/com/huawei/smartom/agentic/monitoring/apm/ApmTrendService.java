/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmTrendAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmTrendViewConfig;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * APM 趋势图查询业务编排。
 *
 * <p>对入参做规约校验：
 * <ul>
 *   <li>{@code view_config} 必填非 {@code null}；其中 {@code view_type} 必须 ∈
 *       {@code trend / sumtable / rawtable}，{@code metric_set} 非空</li>
 *   <li>{@code start_time} / {@code end_time} 非空（原样透传给上游，本层不做格式校验）</li>
 *   <li>{@code business_id}（请求字段或 {@code huaweicloud.apm-business-id} 配置）必须有
 *       其一非空，否则上游会因缺 {@code x-business-id} 头报错</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Service
public class ApmTrendService {

    private static final Set<String> VALID_VIEW_TYPES = Set.of("trend", "sumtable", "rawtable");

    private final ApmTrendAdapter adapter;
    private final Long defaultBusinessId;

    /**
     * 构造一个由指定 adapter + 配置支撑的 {@code ApmTrendService}。
     *
     * @param adapter    APM 趋势图 adapter
     * @param defaultBusinessId 非敏感的默认 APM business id
     */
    public ApmTrendService(ApmTrendAdapter adapter,
            @Value("${huaweicloud.apm-business-id:#{null}}") Long defaultBusinessId) {
        this.adapter = adapter;
        this.defaultBusinessId = defaultBusinessId;
    }

    /**
     * 校验入参后委托 adapter 查询趋势图。
     *
     * @param request 查询请求，不能为 {@code null}
     * @return 曲线列表 + 最新数据点时间
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public ApmTrendResponse showTrend(ApmTrendRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        ApmTrendViewConfig view = Validations.requireNonNull(request.viewConfig(), "view_config");
        Validations.requireNonBlank(view.viewType(), "view_config.view_type");
        if (!VALID_VIEW_TYPES.contains(view.viewType())) {
            throw new InvalidParamException(
                    "view_config.view_type must be one of trend/sumtable/rawtable, got: "
                            + view.viewType());
        }
        Validations.requireNonBlank(view.metricSet(), "view_config.metric_set");
        Validations.requireNonBlank(request.startTime(), "start_time");
        Validations.requireNonBlank(request.endTime(), "end_time");
        Long effective = request.businessId() != null
                ? request.businessId()
                : defaultBusinessId;
        if (effective == null) {
            throw new InvalidParamException(
                    "business_id is required (request.business_id is null and "
                            + "huaweicloud.apm-business-id is not configured)");
        }
        return adapter.showTrend(request);
    }
}
