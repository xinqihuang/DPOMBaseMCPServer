/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.ApmAlarmAdapter;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmNotifyResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

/**
 * APM 告警查询业务编排。
 *
 * <p>T20 仅含 {@code listAlarmData}；T21 将在此追加 {@code listAlarmNotify}。
 *
 * <p>对入参做规约校验：
 * <ul>
 *   <li>{@code business_id}（请求字段或 {@code huaweicloud.apm-business-id} 配置）必须有
 *       其一非空，否则上游会因缺 {@code x-business-id} 头报错</li>
 *   <li>{@code page} 非 null 时 ≥ 1；{@code pageSize} 非 null 时 ∈ [1, 100]</li>
 *   <li>其它过滤参数（appName / keyword / status / alarmLevel 等）按上游约束透传，
 *       service 层不做集合校验</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Service
public class ApmAlarmService {

    private static final int PAGE_MIN = 1;
    private static final int PAGE_SIZE_MIN = 1;
    private static final int PAGE_SIZE_MAX = 100;

    private final ApmAlarmAdapter adapter;
    private final HuaweiCloudProperties properties;

    /**
     * 构造一个由指定 adapter + 配置支撑的 {@code ApmAlarmService}。
     *
     * @param adapter    APM 告警 adapter
     * @param properties 华为云配置，用于读取默认 APM business id
     */
    public ApmAlarmService(ApmAlarmAdapter adapter, HuaweiCloudProperties properties) {
        this.adapter = adapter;
        this.properties = properties;
    }

    /**
     * 校验入参后委托 adapter 查询告警列表。
     *
     * @param request 查询请求，不能为 null
     * @return 告警列表 + 总数
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public ApmAlarmDataResponse listAlarmData(ApmAlarmDataRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listAlarmData(request);
    }

    /**
     * 校验入参后委托 adapter 查询告警通知投递记录。
     *
     * @param request 查询请求，不能为 null
     * @return 通知投递列表 + 总数
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public ApmAlarmNotifyResponse listAlarmNotify(ApmAlarmNotifyRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validateNotify(request);
        return adapter.listAlarmNotify(request);
    }

    private void validate(ApmAlarmDataRequest request) {
        Long effective = request.businessId() != null
                ? request.businessId()
                : properties.getApmBusinessId();
        if (effective == null) {
            throw new InvalidParamException(
                    "business_id is required (request.business_id is null and "
                            + "huaweicloud.apm-business-id is not configured)");
        }
        if (request.page() != null && request.page() < PAGE_MIN) {
            throw new InvalidParamException("page must be >= " + PAGE_MIN + ", got: " + request.page());
        }
        if (request.pageSize() != null
                && (request.pageSize() < PAGE_SIZE_MIN || request.pageSize() > PAGE_SIZE_MAX)) {
            throw new InvalidParamException(
                    "page_size must be in [" + PAGE_SIZE_MIN + ", " + PAGE_SIZE_MAX
                            + "], got: " + request.pageSize());
        }
    }

    private void validateNotify(ApmAlarmNotifyRequest request) {
        if (request.alarmDataId() == null) {
            throw new InvalidParamException("alarm_data_id is required");
        }
        if (request.alarmDataId() <= 0) {
            throw new InvalidParamException(
                    "alarm_data_id must be > 0, got: " + request.alarmDataId());
        }
        Long effective = request.businessId() != null
                ? request.businessId()
                : properties.getApmBusinessId();
        if (effective == null) {
            throw new InvalidParamException(
                    "business_id is required (request.business_id is null and "
                            + "huaweicloud.apm-business-id is not configured)");
        }
        if (request.page() != null && request.page() < PAGE_MIN) {
            throw new InvalidParamException("page must be >= " + PAGE_MIN + ", got: " + request.page());
        }
        if (request.pageSize() != null
                && (request.pageSize() < PAGE_SIZE_MIN || request.pageSize() > PAGE_SIZE_MAX)) {
            throw new InvalidParamException(
                    "page_size must be in [" + PAGE_SIZE_MIN + ", " + PAGE_SIZE_MAX
                            + "], got: " + request.pageSize());
        }
    }
}
