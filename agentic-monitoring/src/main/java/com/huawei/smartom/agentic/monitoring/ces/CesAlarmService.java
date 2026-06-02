/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesMetricsAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListAlarmsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesPatterns;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * CES 告警历史查询的业务编排。
 *
 * <p>对 {@code list_alarms} 工具入参进行规约校验：
 * <ul>
 *   <li>{@code limit} 范围 [1, 100]，{@code start} 非负</li>
 *   <li>{@code alarmStatus}（若提供）必须为 {@code ok/alarm/insufficient_data/invalid}</li>
 *   <li>{@code alarmLevel}（若提供）必须为 1/2/3/4</li>
 *   <li>{@code namespace}（若提供）必须形如 {@code SYS.ECS}</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class CesAlarmService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("ok", "alarm", "insufficient_data", "invalid");

    private static final Set<Integer> ALLOWED_LEVELS = Set.of(1, 2, 3, 4);

    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 100;

    private final CesMetricsAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code CesAlarmService}。
     *
     * @param adapter 执行实际 SDK 调用的 CES adapter
     */
    public CesAlarmService(CesMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行查询。
     *
     * @param request 告警历史查询请求，不能为 null
     * @return 告警历史摘要列表
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public CesListAlarmsResponse listAlarms(CesListAlarmsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listAlarms(request);
    }

    private void validate(CesListAlarmsRequest request) {
        if (request.limit() == null || request.limit() < LIMIT_MIN || request.limit() > LIMIT_MAX) {
            throw new InvalidParamException(
                    "limit must be in [" + LIMIT_MIN + "," + LIMIT_MAX + "]");
        }
        if (request.start() == null || request.start() < 0) {
            throw new InvalidParamException("start must be >= 0");
        }
        if (request.alarmStatus() != null && !ALLOWED_STATUSES.contains(request.alarmStatus())) {
            throw new InvalidParamException(
                    "alarm_status must be one of ok/alarm/insufficient_data/invalid, got: "
                            + request.alarmStatus());
        }
        if (request.alarmLevel() != null && !ALLOWED_LEVELS.contains(request.alarmLevel())) {
            throw new InvalidParamException(
                    "alarm_level must be 1/2/3/4, got: " + request.alarmLevel());
        }
        if (request.namespace() != null && !CesPatterns.NAMESPACE.matcher(request.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid, expected like 'SYS.ECS'");
        }
    }
}
