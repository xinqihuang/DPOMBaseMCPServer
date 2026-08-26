/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressWindow;
import java.util.List;

/**
  * 同一持久化 progress log 的写入与恢复读取端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface ProgressPort {
    /**
     * 追加权威进度记录。
     *
     * @param progress 进度记录
     */
    void append(ProgressRecord progress);

    /**
     * 读取指定序号之后的进度记录。
     *
     * @param investigationId 调查身份
     * @param sequenceExclusive 排他起始序号
     * @param limit 最大记录数
     * @return 有序进度记录
     */
    List<ProgressRecord> after(String investigationId, long sequenceExclusive, int limit);

    /**
     * 读取带保留边界的有界进度窗口。
     *
     * @param investigationId 调查身份
     * @param sequenceExclusive 排他起始序号
     * @param limit 最大记录数
     * @return 进度窗口
     */
    ProgressWindow window(String investigationId, long sequenceExclusive, int limit);
}
