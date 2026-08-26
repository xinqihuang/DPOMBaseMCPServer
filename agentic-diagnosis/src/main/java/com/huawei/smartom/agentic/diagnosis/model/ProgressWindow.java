/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.util.List;

/**
 * 持久化进度日志的有界窗口。
 *
 * @param oldestSequence 当前保留的最早序号，空日志为零
 * @param latestSequence 当前最新序号，空日志为零
 * @param records 按序号递增的记录
 * @author Codex
 * @since 2026-08-25
 */
public record ProgressWindow(long oldestSequence, long latestSequence, List<ProgressRecord> records) {
    public ProgressWindow {
        if (oldestSequence < 0L || latestSequence < 0L || records == null) {
            throw new IllegalArgumentException("progress window");
        }
        records = List.copyOf(records);
    }

    /**
     * 判断请求游标是否落在已删除的保留区间。
     *
     * @param sequenceExclusive 客户端最后已接收序号
     * @return 需要重新同步快照时返回 true
     */
    public boolean requiresResynchronization(long sequenceExclusive) {
        return sequenceExclusive > 0L && oldestSequence > sequenceExclusive + 1L;
    }
}
