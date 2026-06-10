/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * 单条 APM 告警记录，对应 SDK v1 {@code AlarmDataVO} 的无损投影（T19 §4.1 准则，
 * 27 字段全保留）。
 *
 * <p>类型对齐 SDK 注意点：
 * <ul>
 *   <li>{@code collectorId} / {@code versionNumber} 是 {@code Integer}；其它各类 id
 *       （{@code id}、{@code regionAlarmEventId}、{@code instanceId}、{@code envId}、
 *       {@code businessId}、{@code templateId}、{@code alarmRuleId}、{@code monitorItemId}）
 *       是 {@code Long}</li>
 *   <li>各时间字段（{@code gmtCreate}、{@code gmtModify}、{@code alarmFirstTime}、
 *       {@code alarmLastTime}）在 SDK 中是 {@code String}，**不是** {@code OffsetDateTime}
 *       （与 CES v2 告警时间字段不同），DTO 保 {@code String} 透传</li>
 * </ul>
 *
 * @param id                   告警记录 id
 * @param gmtCreate            创建时间字符串
 * @param regionAlarmEventId   region 内告警事件 id
 * @param businessName         业务名
 * @param appName              应用名
 * @param versionNumber        告警规则版本号
 * @param alarmRuleType        告警规则类型字面量
 * @param gmtModify            最后修改时间字符串
 * @param processUnit          进程单元
 * @param region               资源 region
 * @param instanceId           实例 id
 * @param ipAddress            实例 IP
 * @param instanceName         实例名
 * @param envId                环境 id
 * @param businessId           业务 id（响应字段，与请求 header 的 x-business-id 是同一业务概念）
 * @param templateId           模板 id
 * @param alarmRuleId          告警规则 id
 * @param monitorItemId        监控项 id
 * @param collectorId          采集器 id
 * @param collectorName        采集器名
 * @param alarmRuleName        告警规则名
 * @param alarmRuleExpression  告警规则表达式
 * @param alarmFirstTime       首次告警时间字符串
 * @param alarmLastTime        最近告警时间字符串
 * @param alarmLevel           告警级别字面量
 * @param restrainKey          抑制键
 * @param status               告警状态字面量
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmAlarm(
        Long id,
        String gmtCreate,
        Long regionAlarmEventId,
        String businessName,
        String appName,
        Integer versionNumber,
        String alarmRuleType,
        String gmtModify,
        String processUnit,
        String region,
        Long instanceId,
        String ipAddress,
        String instanceName,
        Long envId,
        Long businessId,
        Long templateId,
        Long alarmRuleId,
        Long monitorItemId,
        Integer collectorId,
        String collectorName,
        String alarmRuleName,
        String alarmRuleExpression,
        String alarmFirstTime,
        String alarmLastTime,
        String alarmLevel,
        String restrainKey,
        String status) {
}
