/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * AOM 时序数据查询的统计方式受控枚举（T26，CLAUDE.md §4.3 (a)）。
 *
 * <p>对应 {@code ListSample} 接口 {@code statistics} 数组的元素取值，API 文档中为封闭集。
 * 注意 {@code sampleCount} 为驼峰写法，照 API 原样承载。
 *
 * @author h00884391
 * @since 2026-06-11
 */
public enum AomMetricStatistic {

    /** 最大值。 */
    MAXIMUM("maximum"),

    /** 最小值。 */
    MINIMUM("minimum"),

    /** 求和值。 */
    SUM("sum"),

    /** 平均值。 */
    AVERAGE("average"),

    /** 采样点数。 */
    SAMPLE_COUNT("sampleCount");

    private final String value;

    AomMetricStatistic(String value) {
        this.value = value;
    }

    /**
     * 返回 AOM API 使用的字符串值。
     *
     * @return API 字面量，如 {@code sampleCount}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 将入参字符串解析为枚举。兼容 API 字面量（{@code sampleCount}）与枚举常量名
     * （{@code SAMPLE_COUNT}）两种写法，防 Schema 渲染形式与反序列化规则不一致。
     *
     * @param value 统计方式字面量或常量名
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在受支持集合时抛出
     */
    @JsonCreator
    public static AomMetricStatistic fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("statistic must not be null");
        }
        for (AomMetricStatistic statistic : values()) {
            if (statistic.value.equals(value) || statistic.name().equals(value)) {
                return statistic;
            }
        }
        throw new IllegalArgumentException(
                "unsupported AOM statistic: " + value + ", expected one of "
                        + Arrays.toString(values()));
    }
}
