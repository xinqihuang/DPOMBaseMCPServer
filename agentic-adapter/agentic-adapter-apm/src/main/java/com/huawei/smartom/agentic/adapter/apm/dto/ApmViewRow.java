/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import java.util.List;

/**
 * APM 视图行，对应 SDK v1 {@code ViewRow} 的无损投影
 * （T19 §4.1 准则，2 字段全保留）。
 *
 * <p>页面上一行展示的多个视图。
 *
 * @param viewList 行内视图列表
 * @param title    行标题
 * @author h00884391
 * @since 2026-06-10
 */
public record ApmViewRow(
        List<ApmViewBase> viewList,
        String title) {
}
