/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskProductMetric;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskResource;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.exception.UpstreamException;
import com.huawei.smartom.agentic.monitoring.ces.CesNotificationMaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CesCreateNotificationMaskTool} 单元测试。
 *
 * <p>Tool 层只负责构造 {@link CesCreateNotificationMaskRequest} 并把
 * {@code SmartomException} 转换为 {@link ErrorResponse}，所有业务校验（名称正则、关联类型枚举、
 * 时间窗等）都委托给 {@link CesNotificationMaskService}。本测试覆盖：
 * <ul>
 *   <li>成功路径：14 个入参完整拷贝到请求 DTO</li>
 *   <li>服务层 {@link InvalidParamException} → {@link ErrorResponse}</li>
 *   <li>服务层 {@link UpstreamException} → {@link ErrorResponse}（携带 upstream trace id）</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CesCreateNotificationMaskToolTest {

    private static final String MASK_NAME = "maintenance-mask-1";
    private static final String RELATION_TYPE = "RESOURCE";
    private static final List<String> RELATION_IDS = List.of("alarm-id-1");
    private static final List<NotificationMaskResource> RESOURCES = List.of(
            new NotificationMaskResource(
                    "SYS.ECS",
                    List.of(new NotificationMaskDimension("instance_id", "i-1"))));
    private static final List<String> METRIC_NAMES = List.of("cpu_util");
    private static final List<NotificationMaskProductMetric> PRODUCT_METRICS = List.of(
            new NotificationMaskProductMetric("instance_id", "cpu_util"));
    private static final String RESOURCE_LEVEL = "dimension";
    private static final String PRODUCT_NAME = "ECS";
    private static final String MASK_TYPE = "START_END_TIME";
    private static final String START_DATE = "2026-06-01";
    private static final String START_TIME = "08:00:00";
    private static final String END_DATE = "2026-06-01";
    private static final String END_TIME = "10:00:00";
    private static final String TIMEZONE = "GMT+08:00";

    private CesNotificationMaskService service;
    private CesCreateNotificationMaskTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesNotificationMaskService.class);
        tool = new CesCreateNotificationMaskTool(service);
    }

    @Test
    @DisplayName("Success: every tool param is copied into the request DTO and response passed through")
    void successPassthrough() {
        CesCreateNotificationMaskResponse expected =
                new CesCreateNotificationMaskResponse(List.of("alarm-id-1"), "mask-id-1");
        when(service.createNotificationMask(any(CesCreateNotificationMaskRequest.class)))
                .thenReturn(expected);

        Object result = tool.createNotificationMask(
                MASK_NAME, RELATION_TYPE, RELATION_IDS, RESOURCES, METRIC_NAMES,
                PRODUCT_METRICS, RESOURCE_LEVEL, PRODUCT_NAME, MASK_TYPE,
                START_DATE, START_TIME, END_DATE, END_TIME, TIMEZONE);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesCreateNotificationMaskRequest> captor =
                ArgumentCaptor.forClass(CesCreateNotificationMaskRequest.class);
        verify(service).createNotificationMask(captor.capture());
        CesCreateNotificationMaskRequest req = captor.getValue();
        assertThat(req.maskName()).isEqualTo(MASK_NAME);
        assertThat(req.relationType()).isEqualTo(RELATION_TYPE);
        assertThat(req.relationIds()).isEqualTo(RELATION_IDS);
        assertThat(req.resources()).isEqualTo(RESOURCES);
        assertThat(req.metricNames()).isEqualTo(METRIC_NAMES);
        assertThat(req.productMetrics()).isEqualTo(PRODUCT_METRICS);
        assertThat(req.resourceLevel()).isEqualTo(RESOURCE_LEVEL);
        assertThat(req.productName()).isEqualTo(PRODUCT_NAME);
        assertThat(req.maskType()).isEqualTo(MASK_TYPE);
        assertThat(req.startDate()).isEqualTo(START_DATE);
        assertThat(req.startTime()).isEqualTo(START_TIME);
        assertThat(req.endDate()).isEqualTo(END_DATE);
        assertThat(req.endTime()).isEqualTo(END_TIME);
        assertThat(req.effectiveTimezone()).isEqualTo(TIMEZONE);
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.createNotificationMask(any(CesCreateNotificationMaskRequest.class)))
                .thenThrow(new InvalidParamException(
                        "relation_type must be one of ALARM_RULE/RESOURCE/..."));

        Object result = tool.createNotificationMask(
                MASK_NAME, "BOGUS", RELATION_IDS, RESOURCES, METRIC_NAMES,
                PRODUCT_METRICS, RESOURCE_LEVEL, PRODUCT_NAME, MASK_TYPE,
                START_DATE, START_TIME, END_DATE, END_TIME, TIMEZONE);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("relation_type");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse carrying upstream trace id")
    void upstreamErrorConverted() {
        when(service.createNotificationMask(any(CesCreateNotificationMaskRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-429", null));

        Object result = tool.createNotificationMask(
                MASK_NAME, RELATION_TYPE, RELATION_IDS, RESOURCES, METRIC_NAMES,
                PRODUCT_METRICS, RESOURCE_LEVEL, PRODUCT_NAME, MASK_TYPE,
                START_DATE, START_TIME, END_DATE, END_TIME, TIMEZONE);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-429");
    }
}
