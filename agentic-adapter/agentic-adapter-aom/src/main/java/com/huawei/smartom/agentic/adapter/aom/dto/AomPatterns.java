/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.regex.Pattern;

/**
 * AOM 共用的输入格式正则。集中托管避免在多个 service 之间重复同一份字面量。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public final class AomPatterns {

    /**
     * AOM namespace 取值范围：
     * <ul>
     *   <li>{@code PAAS.CONTAINER} / {@code PAAS.NODE} / {@code PAAS.SLA} / {@code PAAS.AGGR}</li>
     *   <li>{@code CUSTOMMETRICS}</li>
     *   <li>自定义服务名：字母开头，长度 [3, 64]，可含数字 / 下划线</li>
     * </ul>
     */
    public static final Pattern NAMESPACE = Pattern.compile(
            "^(PAAS\\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_]{2,63})$");

    /**
     * AOM 时间范围参数格式：{@code startTimeInMillis.endTimeInMillis.durationInMinutes}，
     * 起止时间可为 {@code -1} 表示由服务端推算。{@code query_aom_metric_data} 与
     * {@code list_aom_events} 共用。
     */
    public static final Pattern TIME_RANGE = Pattern.compile(
            "^(-1|\\d{1,16})\\.(-1|\\d{1,16})\\.\\d{1,7}$");

    private AomPatterns() {
        // 工具类，禁止实例化
    }
}
