/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 单个调用链事件，对应 SDK {@code SpanEventInfo} 的无损投影（§4.1，38 字段全量）。
 *
 * <p>同一 record 同时承载 {@code show_trace_events}（列表元素）与 {@code show_event_detail}
 * （单对象，tags/attachment 内容更全）两个响应。注意 {@code next_spanId} 是 SDK 原样的
 * 混合命名 JSON 键，照抄不规范化。{@code tags} 中的超长内容（完整堆栈/完整 SQL）以 clob
 * 引用出现，用 {@code show_clob_detail} 取全文。
 *
 * @param envName            环境名称
 * @param appName            组件名称
 * @param indent             树形缩进层级
 * @param region             区域
 * @param hostName           主机名
 * @param ipAddress          IP 地址
 * @param instanceName       实例名称
 * @param eventId            事件 id（喂给 {@code show_event_detail}）
 * @param nextSpanId         下一个 span id（JSON 键 {@code next_spanId}，SDK 原样）
 * @param sourceEventId      来源事件 id
 * @param method             方法名
 * @param childrenEventCount 子事件数量
 * @param discard            丢弃统计列表
 * @param argument           调用参数摘要
 * @param attachment         附加键值对，照 SDK 原样承载
 * @param globalTraceId      全局 trace id
 * @param globalPath         全局调用路径
 * @param traceId            trace id
 * @param spanId             span id（喂给 {@code show_event_detail}）
 * @param envId              环境 id（喂给 {@code show_event_detail} / {@code show_clob_detail}）
 * @param instanceId         实例 id
 * @param appId              组件 id
 * @param bizId              应用（business）id
 * @param domainId           租户 domain id
 * @param source             调用来源
 * @param realSource         真实来源
 * @param startTime          开始时间（UTC 毫秒）
 * @param timeUsed           耗时（毫秒）
 * @param code               状态码
 * @param className          类名
 * @param isAsync            是否异步调用
 * @param tags               事件标签（含 SQL / 异常类名 / HTTP 状态等根因线索），照原样承载
 * @param hasError           是否发生错误
 * @param errorReasons       错误原因
 * @param type               事件类型
 * @param httpMethod         HTTP 方法
 * @param bizCode            业务码
 * @param id                 事件主键 id
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmSpanEvent(
        @JsonProperty("env_name") String envName,
        @JsonProperty("app_name") String appName,
        @JsonProperty("indent") Integer indent,
        @JsonProperty("region") String region,
        @JsonProperty("host_name") String hostName,
        @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("instance_name") String instanceName,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("next_spanId") String nextSpanId,
        @JsonProperty("source_event_id") String sourceEventId,
        @JsonProperty("method") String method,
        @JsonProperty("children_event_count") Integer childrenEventCount,
        @JsonProperty("discard") List<ApmDiscardInfo> discard,
        @JsonProperty("argument") String argument,
        @JsonProperty("attachment") Map<String, String> attachment,
        @JsonProperty("global_trace_id") String globalTraceId,
        @JsonProperty("global_path") String globalPath,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("span_id") String spanId,
        @JsonProperty("env_id") Long envId,
        @JsonProperty("instance_id") Long instanceId,
        @JsonProperty("app_id") Long appId,
        @JsonProperty("biz_id") Long bizId,
        @JsonProperty("domain_id") Integer domainId,
        @JsonProperty("source") String source,
        @JsonProperty("real_source") String realSource,
        @JsonProperty("start_time") Long startTime,
        @JsonProperty("time_used") Long timeUsed,
        @JsonProperty("code") Integer code,
        @JsonProperty("class_name") String className,
        @JsonProperty("is_async") Boolean isAsync,
        @JsonProperty("tags") Map<String, String> tags,
        @JsonProperty("has_error") Boolean hasError,
        @JsonProperty("error_reasons") String errorReasons,
        @JsonProperty("type") String type,
        @JsonProperty("http_method") String httpMethod,
        @JsonProperty("biz_code") String bizCode,
        @JsonProperty("id") String id) {
}
