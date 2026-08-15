/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts;

import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogContextResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogGroupsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsRequest;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogsResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 LTS（Log Tank Service）日志查询适配器。
 *
 * <p>实现类内部完成限流、重试以及异常映射，失败时仅向上抛出 {@link SmartomException} 体系中的异常。
 *
 * @author h00884391
 * @since 2026-06-03
 */
public interface LtsLogAdapter {

    /**
     * 列出当前项目可访问的 LTS 日志组。
     *
     * @return 日志组列表
     * @throws SmartomException 上游调用失败
     */
    LtsListLogGroupsResponse listLogGroups();

    /**
     * 按可选名称条件列出 LTS 日志流。
     *
     * @param request 名称过滤条件，不能为 {@code null}
     * @return 日志流列表
     * @throws SmartomException 上游调用失败
     */
    LtsListLogStreamsResponse listLogStreams(LtsListLogStreamsRequest request);

    /**
     * 按时间区间 / 关键字 / 标签 / SQL 查询日志内容。
     * 对应 LTS {@code ListLogs}（{@code POST .../streams/{log_stream_id}/content/query}）。
     *
     * @param request 查询请求（不能为 null）
     * @return 命中日志列表（或 SQL 分析结果）及完成标志
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    LtsListLogsResponse listLogs(LtsListLogsRequest request);

    /**
     * 给定一条目标日志的 {@code line_num} 与 {@code __time__} 游标，拉取其前后 N 条上下文。
     * 对应 LTS {@code ListLogContext}（{@code POST .../streams/{log_stream_id}/context}）。
     *
     * @param request 上下文查询请求（不能为 null）
     * @return 前后上下文条目列表及计数
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    LtsListLogContextResponse listLogContext(LtsListLogContextRequest request);
}
