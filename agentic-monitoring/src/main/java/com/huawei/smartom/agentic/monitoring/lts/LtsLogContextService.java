/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.lts;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapter;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.stereotype.Service;

/**
 * LTS 日志上下文查询业务编排。
 *
 * <p>对 {@code query_lts_log_context} 工具入参按 spec §3.2 做规约校验：
 * <ul>
 *   <li>{@code logGroupId} / {@code logStreamId} 必填、非空白</li>
 *   <li>首次模式（无 {@code scrollId}）下 {@code lineNum} 与 {@code cursorTime} 必须同时给</li>
 *   <li>scroll 模式（{@code scrollId} 非空）下 {@code lineNum} / {@code cursorTime} 可同时给或同时不给，
 *       单边给视为非法</li>
 *   <li>{@code backwardsSize} / {@code forwardsSize} 非 null 时取值 {@code [0, 500]}</li>
 *   <li>两个 size 不允许同时为 0（双 0 上游返空数组，对 Agent 无意义）</li>
 * </ul>
 *
 * @author h00884391
 * @since 2026-06-03
 */
@Service
public class LtsLogContextService {

    private static final int SIZE_MIN = 0;
    private static final int SIZE_MAX = 500;

    private final LtsLogAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code LtsLogContextService}。
     *
     * @param adapter 执行实际 SDK 调用的 LTS adapter
     */
    public LtsLogContextService(LtsLogAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行上下文查询。
     *
     * @param request 查询请求，不能为 null
     * @return 目标日志前后上下文条目及计数
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public LtsListLogContextResponse queryContext(LtsListLogContextRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listLogContext(request);
    }

    private void validate(LtsListLogContextRequest request) {
        Validations.requireNonBlank(request.logGroupId(), "log_group_id");
        Validations.requireNonBlank(request.logStreamId(), "log_stream_id");

        boolean hasScroll = !Validations.isBlank(request.scrollId());
        boolean lineMissing = Validations.isBlank(request.lineNum());
        boolean cursorMissing = Validations.isBlank(request.cursorTime());

        if (!hasScroll) {
            if (lineMissing || cursorMissing) {
                throw new InvalidParamException(
                        "either (line_num + cursor_time) or scroll_id is required");
            }
        }
        else {
            if (lineMissing != cursorMissing) {
                throw new InvalidParamException(
                        "line_num and cursor_time must be provided together");
            }
        }

        validateSize(request.backwardsSize(), "backwards_size");
        validateSize(request.forwardsSize(), "forwards_size");

        if (Integer.valueOf(0).equals(request.backwardsSize())
                && Integer.valueOf(0).equals(request.forwardsSize())) {
            throw new InvalidParamException(
                    "at least one of backwards_size / forwards_size must be > 0");
        }
    }

    private void validateSize(Integer value, String field) {
        if (value != null && (value < SIZE_MIN || value > SIZE_MAX)) {
            throw new InvalidParamException(
                    field + " must be in [" + SIZE_MIN + ", " + SIZE_MAX + "], got: " + value);
        }
    }
}
