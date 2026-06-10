/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

/**
 * APM 采集器类别信息，对应 SDK v1 {@code CollectorCategoryInfo} 的无损投影
 * （T19 §4.1 准则，4 字段全保留）。
 *
 * @param categoryId   类别 id
 * @param categoryName 类别英文名
 * @param displayName  类别展示名
 * @param sequence     展示序号
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmCollectorCategoryInfo(
        Integer categoryId,
        String categoryName,
        String displayName,
        Integer sequence) {
}
