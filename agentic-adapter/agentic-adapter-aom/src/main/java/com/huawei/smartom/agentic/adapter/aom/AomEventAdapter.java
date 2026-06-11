/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom;

import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse;

/**
 * AOM 事件/告警查询能力的 adapter 抽象（T27）。
 *
 * <p>负责把入参 DTO 转为华为云 SDK 请求、执行调用，并把 SDK 响应无损投影回输出 DTO；
 * SDK 类型不得泄漏到本接口之外（CLAUDE.md §4.1）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
public interface AomEventAdapter {

    /**
     * 查询 AOM 事件与告警（AOM v2 {@code ListEvents}）。
     *
     * @param request 请求 DTO，不可为 {@code null}
     * @return 无损投影后的事件列表响应
     */
    AomListEventsResponse listEvents(AomListEventsRequest request);
}
