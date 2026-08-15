/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.lts;

import com.huawei.smartom.agentic.adapter.lts.LtsLogAdapter;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogGroupsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsResponse;

import org.springframework.stereotype.Service;

/**
 * 编排 LTS 日志组与日志流的只读发现调用。
 *
 * @author OpenAI
 * @since 2026-08-15
 */
@Service
public class LtsDiscoveryService {

    private final LtsLogAdapter adapter;

    /**
     * 创建 LTS 发现服务。
     *
     * @param adapter LTS 适配器
     */
    public LtsDiscoveryService(LtsLogAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 列出日志组。
     *
     * @return 日志组列表
     */
    public LtsListLogGroupsResponse listLogGroups() {
        return adapter.listLogGroups();
    }

    /**
     * 列出日志流。
     *
     * @param logGroupName 可选日志组名称
     * @param logStreamName 可选日志流名称
     * @return 日志流列表
     */
    public LtsListLogStreamsResponse listLogStreams(String logGroupName, String logStreamName) {
        return adapter.listLogStreams(new LtsListLogStreamsRequest(logGroupName, logStreamName));
    }
}
