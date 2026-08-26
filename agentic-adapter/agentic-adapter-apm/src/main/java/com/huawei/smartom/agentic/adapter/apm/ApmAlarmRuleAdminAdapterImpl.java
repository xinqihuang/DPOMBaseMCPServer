/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusUpstreamResponse;
import com.huawei.smartom.agentic.adapter.apm.transport.ApmAlarmRuleAdminTransport;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.huaweicloud.sdk.apm.v1.region.ApmRegion;
import com.huaweicloud.sdk.core.http.HttpMethod;
import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;
import com.huaweicloud.sdk.core.utils.JsonUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 基于华为云未生成 REST 接口的 {@link ApmAlarmRuleAdminAdapter} 实现。
 *
 * <p>该实现仅执行一次状态更新，不使用只读调用链中的自动重试器。
 *
 * @author h00884391
 * @since 2026-08-26
 */
@Component
public class ApmAlarmRuleAdminAdapterImpl implements ApmAlarmRuleAdminAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ApmAlarmRuleAdminAdapterImpl.class);

    private static final String UPDATE_STATUS_PATH = "/v2/alarm-center/rule/update-rule-disable";
    private static final String SUCCESS_MARKER = "ok";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ApmAlarmRuleAdminTransport transport;
    private final HuaweiCloudProperties properties;
    private final SdkExceptionMapper exceptionMapper;

    /**
     * 构造 APM 告警规则管理适配器。
     *
     * @param transport       已配置认证的 HTTP 传输
     * @param properties      华为云配置，用于解析 APM region endpoint
     * @param exceptionMapper SDK 连接和超时异常映射器
     */
    public ApmAlarmRuleAdminAdapterImpl(
            ApmAlarmRuleAdminTransport transport,
            HuaweiCloudProperties properties,
            SdkExceptionMapper exceptionMapper) {
        this.transport = transport;
        this.properties = properties;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public ApmAlarmRuleStatusResponse updateAlarmRuleStatus(ApmAlarmRuleStatusRequest request) {
        validate(request);
        LOG.info("apm.updateAlarmRuleStatus start, alarmRuleId={}, enable={}",
                request.alarmRuleId(), request.enable());
        try {
            HttpResponse response = transport.execute(buildRequest(request));
            ApmAlarmRuleStatusResponse result = parseResponse(request, response);
            LOG.info("apm.updateAlarmRuleStatus success, alarmRuleId={}, enable={}, upstreamTraceId={}",
                    request.alarmRuleId(), request.enable(), requestId(response));
            return result;
        }
        catch (SmartomException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw exceptionMapper.map(exception);
        }
    }

    private void validate(ApmAlarmRuleStatusRequest request) {
        if (request == null) {
            throw new SmartomException(ErrorCode.INVALID_PARAM, "request must not be null");
        }
        if (request.alarmRuleId() == null || request.alarmRuleId() <= 0) {
            throw new SmartomException(ErrorCode.INVALID_PARAM, "alarmRuleId must be greater than 0");
        }
        if (request.enable() == null) {
            throw new SmartomException(ErrorCode.INVALID_PARAM, "enable must not be null");
        }
    }

    private HttpRequest buildRequest(ApmAlarmRuleStatusRequest request) {
        String endpoint = ApmRegion.valueOf(properties.getApmRegion()).getEndpoint();
        return HttpRequest.newBuilder()
                .withEndpoint(endpoint)
                .withPath(UPDATE_STATUS_PATH)
                .withMethod(HttpMethod.PUT)
                .withContentType("application/json")
                .addHeader("Accept", "application/json")
                .addQueryParam("alarm_rule_id", List.of(request.alarmRuleId().toString()))
                .addQueryParam("enable", List.of(request.enable().toString()))
                .build();
    }

    private ApmAlarmRuleStatusResponse parseResponse(
            ApmAlarmRuleStatusRequest request,
            HttpResponse response) {
        Objects.requireNonNull(response, "upstream response must not be null");
        if (response.getStatusCode() != 200) {
            throw upstreamFailure(response);
        }
        ApmAlarmRuleStatusUpstreamResponse upstream = parseSuccessBody(response);
        return new ApmAlarmRuleStatusResponse(
                request.alarmRuleId(), request.enable(), true, upstream.ok());
    }

    private ApmAlarmRuleStatusUpstreamResponse parseSuccessBody(HttpResponse response) {
        try {
            ApmAlarmRuleStatusUpstreamResponse body = JsonUtils.getDefaultMapper().readValue(
                    response.getBodyAsString(), ApmAlarmRuleStatusUpstreamResponse.class);
            if (body == null || !SUCCESS_MARKER.equals(body.ok())) {
                throw invalidSuccessResponse(response, null);
            }
            return body;
        }
        catch (SmartomException exception) {
            throw exception;
        }
        catch (JsonProcessingException exception) {
            throw invalidSuccessResponse(response, exception);
        }
    }

    private SmartomException upstreamFailure(HttpResponse response) {
        int status = response.getStatusCode();
        ErrorCode code = classify(status);
        return new UpstreamException(
                code,
                "APM rule status update failed with HTTP " + status,
                requestId(response),
                null);
    }

    private SmartomException invalidSuccessResponse(HttpResponse response, Throwable cause) {
        return new UpstreamException(
                ErrorCode.UPSTREAM_ERROR,
                "APM rule status update returned an invalid success response",
                requestId(response),
                cause);
    }

    private ErrorCode classify(int status) {
        if (status == 400) {
            return ErrorCode.INVALID_PARAM;
        }
        if (status == 401 || status == 403) {
            return ErrorCode.UPSTREAM_AUTH_FAILED;
        }
        if (status == 429) {
            return ErrorCode.UPSTREAM_THROTTLED;
        }
        return ErrorCode.UPSTREAM_ERROR;
    }

    private String requestId(HttpResponse response) {
        String requestId = response.getHeader(REQUEST_ID_HEADER);
        if (requestId == null) {
            return response.getHeader(REQUEST_ID_HEADER.toLowerCase());
        }
        return requestId;
    }
}
