/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomMetricsAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomQueryLogsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * AOM 日志查询的业务编排。
 *
 * <p>对 {@code query_logs} 工具入参进行规约校验：
 * <ul>
 *   <li>{@code category} 必填，取值 {@code app_log/node_log/custom_log}</li>
 *   <li>{@code startTime} / {@code endTime} 必填，毫秒级 UTC 时间戳</li>
 *   <li>{@code endTime > startTime}</li>
 *   <li>{@code pageSize} 范围 [1, 1000]</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class AomLogService {

    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("app_log", "node_log", "custom_log");

    private static final int PAGE_SIZE_MIN = 1;
    private static final int PAGE_SIZE_MAX = 1000;

    private final AomMetricsAdapter adapter;

    /**
     * 构造一个 {@code AomLogService}。
     *
     * @param adapter 执行实际 SDK 调用的 AOM adapter
     */
    public AomLogService(AomMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行查询。
     *
     * @param request 日志查询请求，不能为 null
     * @return 日志查询响应（{@code result} 为上游原始 JSON 字符串）
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public AomQueryLogsResponse queryLogs(AomQueryLogsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.queryLogs(request);
    }

    private void validate(AomQueryLogsRequest request) {
        if (request.category() == null || !ALLOWED_CATEGORIES.contains(request.category())) {
            throw new InvalidParamException(
                    "category must be one of app_log/node_log/custom_log, got: " + request.category());
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new InvalidParamException("start_time and end_time are required (millis)");
        }
        if (request.endTime() <= request.startTime()) {
            throw new InvalidParamException(
                    "end_time must be strictly greater than start_time");
        }
        if (request.pageSize() == null
                || request.pageSize() < PAGE_SIZE_MIN
                || request.pageSize() > PAGE_SIZE_MAX) {
            throw new InvalidParamException(
                    "page_size must be in [" + PAGE_SIZE_MIN + ", " + PAGE_SIZE_MAX + "]");
        }
    }
}
