/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * AOM {@code ListEvents} 接口的查询类型受控枚举（T27，CLAUDE.md §4.3 (a)）。
 *
 * <p>对应 SDK {@code ListEventsRequest.TypeEnum} 的两个取值；该参数可选，
 * 不传时上游返回指定条件下的全部事件与告警。
 *
 * @author h00884391
 * @since 2026-06-11
 */
public enum AomAlertType {

    /** 活动告警（当前未恢复）。 */
    ACTIVE_ALERT("active_alert"),

    /** 历史告警（已恢复或已结束）。 */
    HISTORY_ALERT("history_alert");

    private final String value;

    AomAlertType(String value) {
        this.value = value;
    }

    /**
     * 返回 AOM API 使用的字符串值。
     *
     * @return API 字面量，如 {@code active_alert}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 将入参字符串解析为枚举。兼容 API 字面量（{@code active_alert}）与枚举常量名
     * （{@code ACTIVE_ALERT}）两种写法——JSON Schema 对枚举的渲染形式可能取后者，
     * 两者都接受可避免 Schema 渲染形式与反序列化规则不一致。
     *
     * @param value 查询类型字面量或常量名
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在受支持集合时抛出
     */
    @JsonCreator
    public static AomAlertType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        for (AomAlertType type : values()) {
            if (type.value.equals(value) || type.name().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "unsupported AOM alert type: " + value + ", expected one of "
                        + Arrays.toString(values()));
    }
}
