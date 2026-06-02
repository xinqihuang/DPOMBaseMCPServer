/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import java.util.regex.Pattern;

/**
 * CES 共用的输入格式正则。集中托管避免在多个 service 之间重复同一份字面量。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public final class CesPatterns {

    /**
     * CES namespace 格式 {@code service.item}：service 须以大写字母开头，长度 [3, 32]；
     * item 由字母 / 数字 / 下划线组成。示例：{@code SYS.ECS}、{@code SYS.RDS}。
     */
    public static final Pattern NAMESPACE =
            Pattern.compile("^[A-Z][A-Za-z0-9]{2,31}\\.[A-Za-z0-9_]+$");

    private CesPatterns() {
        // 工具类，禁止实例化
    }
}
