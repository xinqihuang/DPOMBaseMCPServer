/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import java.util.List;

/**
 * 带保留边界与下一游标的有界进度页。
 *
 * @param oldestSequence 当前保留的最早序号
 * @param latestSequence 当前最新序号
 * @param nextAfter 下一页排他游标
 * @param records 进度记录
 * @author Codex
 * @since 2026-08-25
 */
public record ProgressPageResponse(long oldestSequence, long latestSequence, long nextAfter,
                                   List<ProgressResponse> records) {
    public ProgressPageResponse {
        records = List.copyOf(records);
    }
}
