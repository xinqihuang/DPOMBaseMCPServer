/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * AOM 日志查询的日志类型受控枚举（T26，CLAUDE.md §4.3 (a)）。
 *
 * <p>对应 {@code ListLogItems} 接口的 {@code category} 字段，API 文档中为封闭集。
 *
 * @author h00884391
 * @since 2026-06-11
 */
public enum AomLogCategory {

    /** 应用日志。 */
    APP_LOG("app_log"),

    /** 主机（节点）日志。 */
    NODE_LOG("node_log"),

    /** 自定义日志。 */
    CUSTOM_LOG("custom_log");

    private final String value;

    AomLogCategory(String value) {
        this.value = value;
    }

    /**
     * 返回 AOM API 使用的字符串值。
     *
     * @return API 字面量，如 {@code app_log}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 将入参字符串解析为枚举。兼容 API 字面量（{@code app_log}）与枚举常量名
     * （{@code APP_LOG}）两种写法，防 Schema 渲染形式与反序列化规则不一致。
     *
     * @param value 日志类型字面量或常量名
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在受支持集合时抛出
     */
    @JsonCreator
    public static AomLogCategory fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        for (AomLogCategory category : values()) {
            if (category.value.equals(value) || category.name().equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException(
                "unsupported AOM log category: " + value + ", expected one of "
                        + Arrays.toString(values()));
    }
}
