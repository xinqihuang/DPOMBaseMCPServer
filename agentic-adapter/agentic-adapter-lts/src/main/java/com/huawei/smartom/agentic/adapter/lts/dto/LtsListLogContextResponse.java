/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import java.util.List;

/**
 * {@code list_log_context} 工具的响应 DTO，对应华为云 LTS {@code ListLogContext} 接口的响应。
 *
 * @param logs             目标日志前后的上下文条目列表，按时间顺序排列
 * @param totalCount       上下文总条数
 * @param backwardsCount   实际返回的前向条数
 * @param forwardsCount    实际返回的后向条数
 * @param isQueryComplete  上游查询是否已完成
 * @author h00884391
 * @since 2026-06-03
 */
public record LtsListLogContextResponse(
        List<LtsLogEntry> logs,
        Integer totalCount,
        Integer backwardsCount,
        Integer forwardsCount,
        Boolean isQueryComplete) {
}
