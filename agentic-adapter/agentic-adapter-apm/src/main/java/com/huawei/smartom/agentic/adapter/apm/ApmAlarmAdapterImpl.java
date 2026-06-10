/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarm;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.resilience.HuaweiCloudInvocation;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.model.AlarmDataListRequest;
import com.huaweicloud.sdk.apm.v1.model.AlarmDataVO;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmDataRequest;
import com.huaweicloud.sdk.apm.v1.model.ListAlarmDataResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于华为云 Java SDK v3.1.177 的 {@link ApmAlarmAdapter} 实现。
 *
 * <p>T20 仅实现 {@code listAlarmData}；T21 在此基础上追加 {@code listAlarmNotify}。
 *
 * @author h00884391
 * @since 2026-06-10
 */
@Component
public class ApmAlarmAdapterImpl implements ApmAlarmAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ApmAlarmAdapterImpl.class);

    private static final String RATE_LIMITER_NAME = "apm-readonly";
    private static final String RETRY_NAME = "huaweicloud-retryable";
    private static final String API_LIST_ALARM_DATA = "apm.listAlarmData";

    private final ApmClient apmClient;
    private final HuaweiCloudInvocation invocation;
    private final HuaweiCloudProperties properties;

    /**
     * 构造 {@code ApmAlarmAdapterImpl}。
     *
     * @param apmClient  已配置好的华为云 APM SDK 客户端
     * @param invocation 限流 / 重试 / 异常映射调用助手
     * @param properties 华为云配置，用于读取 APM 默认 business id
     */
    public ApmAlarmAdapterImpl(
            ApmClient apmClient,
            HuaweiCloudInvocation invocation,
            HuaweiCloudProperties properties) {
        this.apmClient = apmClient;
        this.invocation = invocation;
        this.properties = properties;
    }

    @Override
    public ApmAlarmDataResponse listAlarmData(ApmAlarmDataRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Long businessId = request.businessId() == null
                ? properties.getApmBusinessId()
                : request.businessId();
        LOG.info("apm.listAlarmData start, businessId={}, page={}, pageSize={}, appName={}, "
                        + "status={}, alarmLevel={}, keyword={}",
                businessId, request.page(), request.pageSize(), request.appName(),
                request.status(), request.alarmLevel(), request.keyword());

        AlarmDataListRequest body = new AlarmDataListRequest()
                .withPage(request.page())
                .withPageSize(request.pageSize())
                .withRegion(request.region())
                .withAppName(request.appName())
                .withBusinessId(request.businessIdFilter())
                .withMonitorItemId(request.monitorItemId())
                .withStatus(request.status())
                .withAlarmLevel(request.alarmLevel())
                .withKeyword(request.keyword())
                .withAlarmStartTime(request.alarmStartTime())
                .withAlarmEndTime(request.alarmEndTime())
                .withCollectorId(request.collectorId())
                .withIpAddress(request.ipAddress())
                .withEnvList(request.envList());

        ListAlarmDataRequest sdkRequest = new ListAlarmDataRequest().withBody(body);
        if (businessId != null) {
            sdkRequest.setXBusinessId(businessId);
        }

        ListAlarmDataResponse sdkResponse = invocation.execute(
                RATE_LIMITER_NAME, RETRY_NAME, API_LIST_ALARM_DATA,
                () -> apmClient.listAlarmData(sdkRequest));

        return toResponseDto(sdkResponse);
    }

    private ApmAlarmDataResponse toResponseDto(ListAlarmDataResponse sdkResp) {
        List<AlarmDataVO> sdkAlarms = sdkResp.getAlarmDataList() == null
                ? Collections.emptyList()
                : sdkResp.getAlarmDataList();
        List<ApmAlarm> alarms = sdkAlarms.stream()
                .map(this::toApmAlarm)
                .toList();
        return new ApmAlarmDataResponse(alarms, sdkResp.getTotalCount());
    }

    private ApmAlarm toApmAlarm(AlarmDataVO sdk) {
        return new ApmAlarm(
                sdk.getId(),
                sdk.getGmtCreate(),
                sdk.getRegionAlarmEventId(),
                sdk.getBusinessName(),
                sdk.getAppName(),
                sdk.getVersionNumber(),
                sdk.getAlarmRuleType(),
                sdk.getGmtModify(),
                sdk.getProcessUnit(),
                sdk.getRegion(),
                sdk.getInstanceId(),
                sdk.getIpAddress(),
                sdk.getInstanceName(),
                sdk.getEnvId(),
                sdk.getBusinessId(),
                sdk.getTemplateId(),
                sdk.getAlarmRuleId(),
                sdk.getMonitorItemId(),
                sdk.getCollectorId(),
                sdk.getCollectorName(),
                sdk.getAlarmRuleName(),
                sdk.getAlarmRuleExpression(),
                sdk.getAlarmFirstTime(),
                sdk.getAlarmLastTime(),
                sdk.getAlarmLevel(),
                sdk.getRestrainKey(),
                sdk.getStatus());
    }
}
