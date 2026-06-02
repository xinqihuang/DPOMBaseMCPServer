/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 华为云 CES 监控数据查询的聚合方式。
 *
 * <p>对应 CES {@code ShowMetricData} / {@code BatchListMetricData} 接口的 {@code filter} 字段。
 * 取值见 <a href="https://support.huaweicloud.com/intl/en-us/api-ces/">CES API 文档</a>。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public enum CesMetricFilter {

    /** 平均值。 */
    AVERAGE("average"),

    /** 最大值。 */
    MAX("max"),

    /** 最小值。 */
    MIN("min"),

    /** 求和值。 */
    SUM("sum"),

    /** 方差。 */
    VARIANCE("variance");

    private final String value;

    CesMetricFilter(String value) {
        this.value = value;
    }

    /**
     * 返回 CES API 使用的字符串值（小写）。
     *
     * @return CES API filter 字面量
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 将 CES API 字符串解析为枚举。
     *
     * @param value CES filter 字面量（如 {@code average}）
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在已知集合时抛出
     */
    @JsonCreator
    public static CesMetricFilter fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("filter must not be null");
        }
        for (CesMetricFilter filter : values()) {
            if (filter.value.equals(value)) {
                return filter;
            }
        }
        throw new IllegalArgumentException(
                "unknown CES filter: " + value + ", expected one of "
                        + Arrays.toString(values()));
    }
}
