/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * {@code search_apm_application} 工具的请求 DTO，对应华为云 APM {@code SearchApplication}
 * 接口（POST /v1/apm2/openapi/apm-service/app-mgr/search）的入参。
 *
 * <p>API 层 {@code x-business-id}（header）与 {@code business_id}（body）取同一生效值，
 * adapter 统一填充；{@code businessId} / {@code region} 为 {@code null} 时由 adapter
 * 回落到 {@code huaweicloud.apm-business-id} / {@code huaweicloud.apm-region} 配置。
 *
 * @param businessId 应用 id，取自 {@code list_apm_business} 的返回，可为 {@code null}（回落配置）
 * @param region     区域名称（如 {@code cn-north-4}），可为 {@code null}（回落配置）
 * @param page       页码（从 1 开始），为 {@code null} 时默认 1
 * @param pageSize   每页条数，可选，必须不小于 1
 * @param keyword    组件/环境名称关键字过滤，可选
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmSearchApplicationRequest(
        Long businessId,
        String region,
        Integer page,
        Integer pageSize,
        String keyword) {

    /**
     * 紧凑构造器：页码缺省时默认第 1 页。
     */
    public ApmSearchApplicationRequest {
        if (page == null) {
            page = 1;
        }
    }
}
