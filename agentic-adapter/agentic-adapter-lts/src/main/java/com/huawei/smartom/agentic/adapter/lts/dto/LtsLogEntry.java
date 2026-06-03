/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import java.util.Map;

/**
 * 单条 LTS 日志记录，对应 SDK {@code LogContents}。
 *
 * @param content 日志原文
 * @param lineNum 行号游标（纳秒级时间戳），可与 {@code __time__} 配合实现上下文 / 分页查询
 * @param labels  日志附带的标签集合，可能为空但通常包含 hostName / hostIp / containerName 等
 * @author h00884391
 * @since 2026-06-03
 */
public record LtsLogEntry(
        String content,
        String lineNum,
        Map<String, String> labels) {
}
