/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.dto;

import java.util.Map;

/**
 * {@code list_logs} 工具的请求 DTO，对应华为云 LTS
 * {@code POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query}。
 *
 * <p>仅 {@code logGroupId} / {@code logStreamId} 必填；其余字段均为可选过滤 / 分页 / 排序参数。
 * SDK 在请求体中将 {@code startTimeMillis} / {@code endTimeMillis} 表达为 String 形式毫秒时间戳，
 * adapter 在 {@code toSdk*} 转换时负责 {@code String.valueOf(long)} 换算，DTO 对上层暴露 Long 与
 * CES / AOM 风格保持一致。
 *
 * @param logGroupId       日志组 id（必填）
 * @param logStreamId      日志流 id（必填）
 * @param startTimeMillis  搜索起始时间，UTC 毫秒；可为 {@code null}
 * @param endTimeMillis    搜索结束时间，UTC 毫秒；可为 {@code null}
 * @param labels           日志标签过滤，map 形式；可为 {@code null}
 * @param keywords         关键字过滤；可为 {@code null}
 * @param query            SQL/分析查询语句；可为 {@code null}
 * @param isAnalysisQuery  是否走 SQL 分析模式，{@code true} 时响应通过 {@code analysisLogs} 返回；可为 {@code null}
 * @param isCount          是否只返回命中条数；可为 {@code null}
 * @param limit            单页大小；可为 {@code null}
 * @param isDesc           是否倒序；可为 {@code null}
 * @param highlight        是否高亮关键字；可为 {@code null}
 * @param isIterative      是否启用迭代查询；可为 {@code null}
 * @param searchType       分页方向：{@code forwards} / {@code backwards}；首次查询传 {@code null}
 *                         （SDK 默认 {@code init}）。按 ADR-004 用 String 容忍未来扩展
 * @param lineNum          游标分页：上次结果尾行的行号；可为 {@code null}
 * @param cursorTime       游标分页：与 {@code lineNum} 配对的 {@code __time__} 时间戳字符串；可为 {@code null}
 * @param scrollId         scroll 模式分页 id；可为 {@code null}
 * @author h00884391
 * @since 2026-06-03
 */
public record LtsListLogsRequest(
        String logGroupId,
        String logStreamId,
        Long startTimeMillis,
        Long endTimeMillis,
        Map<String, String> labels,
        String keywords,
        String query,
        Boolean isAnalysisQuery,
        Boolean isCount,
        Integer limit,
        Boolean isDesc,
        Boolean highlight,
        Boolean isIterative,
        String searchType,
        String lineNum,
        String cursorTime,
        String scrollId) {
}
