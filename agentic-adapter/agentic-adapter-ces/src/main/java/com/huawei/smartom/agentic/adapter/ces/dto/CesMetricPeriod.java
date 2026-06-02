/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 华为云 CES 监控数据查询的聚合粒度（秒）。
 *
 * <p>对应 CES {@code ShowMetricData} / {@code BatchListMetricData} 接口的 {@code period} 字段。
 * 取值见 <a href="https://support.huaweicloud.com/intl/en-us/api-ces/">CES API 文档</a>。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public enum CesMetricPeriod {

    /** 实时数据，不做聚合。 */
    REAL_TIME(1),

    /** 1 分钟粒度。 */
    MIN_1(60),

    /** 5 分钟粒度。 */
    MIN_5(300),

    /** 20 分钟粒度。 */
    MIN_20(1200),

    /** 1 小时粒度。 */
    HOUR_1(3600),

    /** 4 小时粒度。 */
    HOUR_4(14400),

    /** 1 天粒度。 */
    DAY_1(86400);

    private final int seconds;

    CesMetricPeriod(int seconds) {
        this.seconds = seconds;
    }

    /**
     * 返回粒度对应的秒数。
     *
     * @return 秒数
     */
    @JsonValue
    public int getSeconds() {
        return seconds;
    }

    /**
     * 将秒数解析为枚举。
     *
     * @param seconds 粒度秒数（如 60、300、3600）
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code seconds} 不在已知集合时抛出
     */
    @JsonCreator
    public static CesMetricPeriod fromSeconds(int seconds) {
        for (CesMetricPeriod period : values()) {
            if (period.seconds == seconds) {
                return period;
            }
        }
        throw new IllegalArgumentException(
                "unknown CES period: " + seconds + ", expected one of "
                        + Arrays.toString(values()));
    }
}
