/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * AOM 时序数据断点插值策略的受控枚举（T26，CLAUDE.md §4.3 (a)）。
 *
 * <p>对应 {@code ListSample} 接口的 {@code fill_value} 字段，API 文档中为封闭集。
 * 注意 {@link #NULL_FILL} 的取值是<strong>字面量字符串</strong> {@code "null"}，
 * 不是 Java 的 {@code null}，照 API 原样承载。
 *
 * @author h00884391
 * @since 2026-06-11
 */
public enum AomFillValue {

    /** 断点处填 -1。 */
    MINUS_ONE("-1"),

    /** 断点处填 0。 */
    ZERO("0"),

    /** 断点处填字面量 {@code null}（API 默认）。 */
    NULL_FILL("null"),

    /** 断点处填相邻数据的平均值。 */
    AVERAGE("average");

    private final String value;

    AomFillValue(String value) {
        this.value = value;
    }

    /**
     * 返回 AOM API 使用的字符串值。
     *
     * @return API 字面量，如 {@code "null"}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 将入参字符串解析为枚举。兼容 API 字面量（{@code "null"}）与枚举常量名
     * （{@code NULL_FILL}）两种写法，防 Schema 渲染形式与反序列化规则不一致。
     *
     * @param value 插值策略字面量或常量名
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在受支持集合时抛出
     */
    @JsonCreator
    public static AomFillValue fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fill_value must not be null");
        }
        for (AomFillValue fill : values()) {
            if (fill.value.equals(value) || fill.name().equals(value)) {
                return fill;
            }
        }
        throw new IllegalArgumentException(
                "unsupported AOM fill_value: " + value + ", expected one of "
                        + Arrays.toString(values()));
    }
}
