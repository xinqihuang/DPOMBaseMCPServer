/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AOM 时序数据查询的采样粒度受控枚举（T26，CLAUDE.md §4.3 (a)）。
 *
 * <p>对应 {@code ListSample} 接口的 {@code period} 字段（秒），API 文档中为封闭集。
 * 注意取值集合（60/300/900/3600）与 CES 的 {@code CesMetricPeriod}（1/60/300/…）不同，
 * 两者不可混用。
 *
 * @author h00884391
 * @since 2026-06-11
 */
public enum AomMetricPeriod {

    /** 1 分钟。 */
    SEC_60(60),

    /** 5 分钟。 */
    SEC_300(300),

    /** 15 分钟。 */
    SEC_900(900),

    /** 1 小时。 */
    SEC_3600(3600);

    private final int seconds;

    AomMetricPeriod(int seconds) {
        this.seconds = seconds;
    }

    /**
     * 返回采样粒度的秒数（AOM API 取值）。
     *
     * @return 秒数，如 {@code 300}
     */
    @JsonValue
    public int getSeconds() {
        return seconds;
    }

    /**
     * 按秒数查找枚举（程序内使用）。
     *
     * @param seconds 秒数
     * @return 对应的枚举常量
     * @throws IllegalArgumentException 秒数不在受支持集合时抛出
     */
    public static AomMetricPeriod fromSeconds(int seconds) {
        for (AomMetricPeriod period : values()) {
            if (period.seconds == seconds) {
                return period;
            }
        }
        throw new IllegalArgumentException(
                "unsupported AOM period: " + seconds + ", expected one of 60/300/900/3600 (seconds)");
    }

    /**
     * Jackson 反序列化入口。兼容数字（{@code 300}，JSON number 经标量转换以字符串形式传入）、
     * 数字字符串（{@code "300"}）与枚举常量名（{@code SEC_300}）三种写法，
     * 防 Schema 渲染形式与反序列化规则不一致。
     *
     * @param value 秒数或常量名
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在受支持集合时抛出
     */
    @JsonCreator
    public static AomMetricPeriod fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("period must not be null");
        }
        for (AomMetricPeriod period : values()) {
            if (String.valueOf(period.seconds).equals(value) || period.name().equals(value)) {
                return period;
            }
        }
        throw new IllegalArgumentException(
                "unsupported AOM period: " + value + ", expected one of 60/300/900/3600 (seconds)");
    }
}
