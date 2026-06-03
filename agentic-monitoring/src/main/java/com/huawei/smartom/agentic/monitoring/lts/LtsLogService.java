/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.lts;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapter;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * LTS 日志检索业务编排。
 *
 * <p>对 {@code query_lts_logs} 工具入参按 spec §3.2 做规约校验：
 * <ul>
 *   <li>{@code logGroupId} / {@code logStreamId} 必填、非空白</li>
 *   <li>同时给定时间窗时，{@code startTimeMillis} 必须严格小于 {@code endTimeMillis}</li>
 *   <li>{@code limit} 非 null 时取值范围 {@code [1, 5000]}</li>
 *   <li>{@code searchType} 非 null 时必须为 {@code forwards} / {@code backwards}</li>
 *   <li>{@code lineNum} 与 {@code cursorTime} 必须成对出现</li>
 * </ul>
 *
 * <p>按 ADR-004：{@code searchType} 用 {@code Set<String>} 容忍未来扩展，**不**做严格枚举。
 *
 * @author h00884391
 * @since 2026-06-03
 */
@Service
public class LtsLogService {

    private static final Set<String> ALLOWED_SEARCH_TYPES = Set.of("forwards", "backwards");

    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 5000;

    private final LtsLogAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code LtsLogService}。
     *
     * @param adapter 执行实际 SDK 调用的 LTS adapter
     */
    public LtsLogService(LtsLogAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行日志查询。
     *
     * @param request 查询请求，不能为 null
     * @return 日志列表与查询完成标志
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public LtsListLogsResponse queryLogs(LtsListLogsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listLogs(request);
    }

    private void validate(LtsListLogsRequest request) {
        Validations.requireNonBlank(request.logGroupId(), "log_group_id");
        Validations.requireNonBlank(request.logStreamId(), "log_stream_id");

        if (request.startTimeMillis() != null && request.endTimeMillis() != null
                && request.startTimeMillis() >= request.endTimeMillis()) {
            throw new InvalidParamException(
                    "start_time_millis must be strictly less than end_time_millis: from="
                            + request.startTimeMillis() + ", to=" + request.endTimeMillis());
        }

        Integer limit = request.limit();
        if (limit != null && (limit < LIMIT_MIN || limit > LIMIT_MAX)) {
            throw new InvalidParamException(
                    "limit must be in [" + LIMIT_MIN + ", " + LIMIT_MAX + "], got: " + limit);
        }

        String searchType = request.searchType();
        if (searchType != null && !ALLOWED_SEARCH_TYPES.contains(searchType)) {
            throw new InvalidParamException(
                    "search_type must be one of forwards/backwards, got: " + searchType);
        }

        boolean lineNumMissing = Validations.isBlank(request.lineNum());
        boolean cursorTimeMissing = Validations.isBlank(request.cursorTime());
        if (lineNumMissing != cursorTimeMissing) {
            throw new InvalidParamException(
                    "line_num and cursor_time must be provided together");
        }
    }
}
