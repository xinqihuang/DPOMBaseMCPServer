/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

/**
 * {@code list_alarms} 工具的请求 DTO，对应华为云 CES {@code ListAlarmHistories} 接口的入参。
 *
 * <p>除 {@code limit} 与 {@code start} 在紧凑构造中设置默认值外，其余字段均可为 {@code null}，
 * 业务层负责其余校验。
 *
 * @param groupId     资源分组 ID，可选（以 {@code rg} 开头，长度 24）
 * @param alarmId     告警规则 ID，可选（以 {@code al} 开头，长度 24）
 * @param alarmName   告警规则名，可选，长度 [1, 128]
 * @param alarmStatus 告警状态枚举：{@code ok} / {@code alarm} / {@code insufficient_data} / {@code invalid}
 * @param alarmLevel  告警级别 [1, 4]，1=紧急、2=重要、3=次要、4=提示
 * @param namespace   命名空间，例如 {@code SYS.ECS}
 * @param from        时间区间起点，毫秒时间戳字符串（长度 [1, 13]）
 * @param to          时间区间终点，毫秒时间戳字符串
 * @param start       分页偏移，{@code null} 时默认 0
 * @param limit       分页大小，{@code null} 时默认 100，最大 100
 * @author h00884391
 * @since 2026-05-28
 */
public record CesListAlarmsRequest(
        String groupId,
        String alarmId,
        String alarmName,
        String alarmStatus,
        Integer alarmLevel,
        String namespace,
        String from,
        String to,
        Integer start,
        Integer limit) {

    /**
     * 紧凑构造函数，仅完成默认值规范化，不做业务校验。
     */
    public CesListAlarmsRequest {
        if (limit == null) {
            limit = 100;
        }
        if (start == null) {
            start = 0;
        }
    }
}
