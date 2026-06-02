/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.NotificationMaskDimension;
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
 * {@link CesListNotificationMasksTool} 单元测试。
 *
 * <p>该工具为分页只读查询，Tool 层只负责构造 {@link CesListNotificationMasksRequest}、透传响应、
 * 把 {@code SmartomException} 转换为 {@link ErrorResponse}；offset/limit 范围与枚举值校验
 * 全部下沉到 {@link CesNotificationMaskService}。注意 {@code CesListNotificationMasksRequest}
 * 紧凑构造函数会把 {@code null} 的 offset/limit 默认为 0 / 100。本测试覆盖：
 * <ul>
 *   <li>成功路径：14 个入参完整透传，且 null offset/limit 被记录默认值</li>
 *   <li>服务层 {@link InvalidParamException} → {@link ErrorResponse}</li>
 *   <li>服务层 {@link UpstreamException} → {@link ErrorResponse}（携带 upstream trace id）</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CesListNotificationMasksToolTest {

    private static final Integer OFFSET = 10;
    private static final Integer LIMIT = 50;
    private static final String SORT_KEY = "create_time";
    private static final String SORT_DIR = "DESC";
    private static final String RELATION_TYPE = "ALARM_RULE";
    private static final List<String> RELATION_IDS = List.of("alarm-id-1");
    private static final String METRIC_NAME = "cpu_util";
    private static final String RESOURCE_LEVEL = "dimension";
    private static final String MASK_ID = "mask-id-1";
    private static final String MASK_NAME = "maintenance-mask-1";
    private static final String MASK_STATUS = "MASK_EFFECTIVE";
    private static final String RESOURCE_ID = "i-1";
    private static final String NAMESPACE = "SYS.ECS";
    private static final List<NotificationMaskDimension> DIMENSIONS =
            List.of(new NotificationMaskDimension("instance_id", "i-1"));

    private CesNotificationMaskService service;
    private CesListNotificationMasksTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesNotificationMaskService.class);
        tool = new CesListNotificationMasksTool(service);
    }

    @Test
    @DisplayName("Success: every tool param is copied into the request DTO and response passed through")
    void successPassthrough() {
        CesListNotificationMasksResponse expected =
                new CesListNotificationMasksResponse(List.of(), 0);
        when(service.listNotificationMasks(any(CesListNotificationMasksRequest.class)))
                .thenReturn(expected);

        Object result = tool.listNotificationMasks(
                OFFSET, LIMIT, SORT_KEY, SORT_DIR, RELATION_TYPE, RELATION_IDS, METRIC_NAME,
                RESOURCE_LEVEL, MASK_ID, MASK_NAME, MASK_STATUS, RESOURCE_ID, NAMESPACE, DIMENSIONS);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesListNotificationMasksRequest> captor =
                ArgumentCaptor.forClass(CesListNotificationMasksRequest.class);
        verify(service).listNotificationMasks(captor.capture());
        CesListNotificationMasksRequest req = captor.getValue();
        assertThat(req.offset()).isEqualTo(OFFSET);
        assertThat(req.limit()).isEqualTo(LIMIT);
        assertThat(req.sortKey()).isEqualTo(SORT_KEY);
        assertThat(req.sortDir()).isEqualTo(SORT_DIR);
        assertThat(req.relationType()).isEqualTo(RELATION_TYPE);
        assertThat(req.relationIds()).isEqualTo(RELATION_IDS);
        assertThat(req.metricName()).isEqualTo(METRIC_NAME);
        assertThat(req.resourceLevel()).isEqualTo(RESOURCE_LEVEL);
        assertThat(req.maskId()).isEqualTo(MASK_ID);
        assertThat(req.maskName()).isEqualTo(MASK_NAME);
        assertThat(req.maskStatus()).isEqualTo(MASK_STATUS);
        assertThat(req.resourceId()).isEqualTo(RESOURCE_ID);
        assertThat(req.namespace()).isEqualTo(NAMESPACE);
        assertThat(req.dimensions()).isEqualTo(DIMENSIONS);
    }

    @Test
    @DisplayName("Null offset/limit are defaulted by the request DTO compact constructor (0/100)")
    void nullPagingDefaultsApplied() {
        CesListNotificationMasksResponse expected =
                new CesListNotificationMasksResponse(List.of(), 0);
        when(service.listNotificationMasks(any(CesListNotificationMasksRequest.class)))
                .thenReturn(expected);

        Object result = tool.listNotificationMasks(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesListNotificationMasksRequest> captor =
                ArgumentCaptor.forClass(CesListNotificationMasksRequest.class);
        verify(service).listNotificationMasks(captor.capture());
        CesListNotificationMasksRequest req = captor.getValue();
        assertThat(req.offset()).isEqualTo(0);
        assertThat(req.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.listNotificationMasks(any(CesListNotificationMasksRequest.class)))
                .thenThrow(new InvalidParamException("limit must be in [1, 100]"));

        Object result = tool.listNotificationMasks(
                0, 9999, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("limit");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse carrying upstream trace id")
    void upstreamErrorConverted() {
        when(service.listNotificationMasks(any(CesListNotificationMasksRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_THROTTLED, "throttled", "req-throttle", null));

        Object result = tool.listNotificationMasks(
                OFFSET, LIMIT, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_THROTTLED.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-throttle");
    }
}
