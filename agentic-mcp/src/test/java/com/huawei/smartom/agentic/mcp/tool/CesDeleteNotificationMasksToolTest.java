/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksResponse;
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
 * {@link CesDeleteNotificationMasksTool} 单元测试。
 *
 * <p>该工具为写（删除）操作，Tool 层只负责打包 {@link CesDeleteNotificationMasksRequest}、
 * 透传响应，以及把 {@code SmartomException} 转换为 {@link ErrorResponse}；批量大小、空白 ID
 * 等校验全部下沉到 {@link CesNotificationMaskService}。本测试覆盖：
 * <ul>
 *   <li>成功路径：ID 列表完整透传，上游可能只删除部分（response 长度可能短于请求）</li>
 *   <li>服务层 {@link InvalidParamException} → {@link ErrorResponse}</li>
 *   <li>服务层 {@link UpstreamException} → {@link ErrorResponse}（携带 upstream trace id）</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-02
 */
class CesDeleteNotificationMasksToolTest {

    private static final List<String> IDS = List.of("mask-1", "mask-2", "mask-3");

    private CesNotificationMaskService service;
    private CesDeleteNotificationMasksTool tool;

    @BeforeEach
    void setUp() {
        service = mock(CesNotificationMaskService.class);
        tool = new CesDeleteNotificationMasksTool(service);
    }

    @Test
    @DisplayName("Success: ids list is forwarded into the request DTO and partial-delete response passed through")
    void successPassthrough() {
        // Upstream may only confirm deletion of a subset (missing ids = already gone).
        CesDeleteNotificationMasksResponse expected =
                new CesDeleteNotificationMasksResponse(List.of("mask-1", "mask-2"));
        when(service.deleteNotificationMasks(any(CesDeleteNotificationMasksRequest.class)))
                .thenReturn(expected);

        Object result = tool.deleteNotificationMasks(IDS);
        assertThat(result).isSameAs(expected);

        ArgumentCaptor<CesDeleteNotificationMasksRequest> captor =
                ArgumentCaptor.forClass(CesDeleteNotificationMasksRequest.class);
        verify(service).deleteNotificationMasks(captor.capture());
        CesDeleteNotificationMasksRequest req = captor.getValue();
        assertThat(req.notificationMaskIds()).isEqualTo(IDS);
    }

    @Test
    @DisplayName("Service InvalidParamException is converted to ErrorResponse with INVALID_PARAM")
    void serviceInvalidParamConverted() {
        when(service.deleteNotificationMasks(any(CesDeleteNotificationMasksRequest.class)))
                .thenThrow(new InvalidParamException(
                        "notification_mask_ids size must be in [1, 100], got: 0"));

        Object result = tool.deleteNotificationMasks(List.of());

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM.name());
        assertThat(err.retryable()).isFalse();
        assertThat(err.upstreamTraceId()).isNull();
        assertThat(err.errorMessage()).contains("notification_mask_ids");
    }

    @Test
    @DisplayName("UpstreamException is converted to ErrorResponse carrying upstream trace id")
    void upstreamErrorConverted() {
        when(service.deleteNotificationMasks(any(CesDeleteNotificationMasksRequest.class)))
                .thenThrow(new UpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "server error", "req-500", null));

        Object result = tool.deleteNotificationMasks(IDS);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) result;
        assertThat(err.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR.name());
        assertThat(err.retryable()).isTrue();
        assertThat(err.upstreamTraceId()).isEqualTo("req-500");
    }
}
