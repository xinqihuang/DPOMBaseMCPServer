/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code query_traces} 工具的请求 DTO，对应华为云 APM {@code ShowSpanSearch} 接口的入参。
 *
 * @param businessId       APM 应用 id（{@code x-business-id} 头部），可选；为空时使用配置项默认值
 * @param startTimeString  起始时间字符串（如 {@code 2026-05-28 10:00:00}），可选
 * @param endTimeString    结束时间字符串，可选
 * @param traceId          按 traceId 精确过滤，可选
 * @param source           入口 url 或方法，模糊匹配，可选
 * @param hasError         是否仅返回出错的 span，可选
 * @param timeUsedMin      最小耗时（毫秒），可选
 * @param page             页码，从 1 开始，{@code null} 时默认 1
 * @param pageSize         单页大小，{@code null} 时默认 50
 * @author h00884391
 * @since 2026-05-28
 */
public record ApmQueryTracesRequest(
        Long businessId,
        String startTimeString,
        String endTimeString,
        String traceId,
        String source,
        Boolean hasError,
        Long timeUsedMin,
        Integer page,
        Integer pageSize) {

    /**
     * 紧凑构造函数，设置默认 page/pageSize，不做业务校验。
     */
    public ApmQueryTracesRequest {
        if (page == null) {
            page = 1;
        }
        if (pageSize == null) {
            pageSize = 50;
        }
    }
}
