/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AomMetricPeriod} 受控枚举的单元测试（T26）。
 *
 * <p>额外覆盖 Jackson 数字反序列化：JSON number 经标量转换进入 {@code fromValue(String)}。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomMetricPeriodTest {

    @Test
    @DisplayName("enum contains exactly 60/300/900/3600 seconds (differs from CES periods)")
    void containsExactlyFourValues() {
        assertThat(AomMetricPeriod.values())
                .extracting(AomMetricPeriod::getSeconds)
                .containsExactlyInAnyOrder(60, 300, 900, 3600);
    }

    @Test
    @DisplayName("fromValue accepts digit string and constant name; fromSeconds accepts int")
    void fromValueAcceptsAllForms() {
        assertThat(AomMetricPeriod.fromValue("300")).isEqualTo(AomMetricPeriod.SEC_300);
        assertThat(AomMetricPeriod.fromValue("SEC_300")).isEqualTo(AomMetricPeriod.SEC_300);
        assertThat(AomMetricPeriod.fromSeconds(3600)).isEqualTo(AomMetricPeriod.SEC_3600);
    }

    @Test
    @DisplayName("Jackson deserializes a JSON number into the enum")
    void jacksonNumberDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readValue("300", AomMetricPeriod.class))
                .isEqualTo(AomMetricPeriod.SEC_300);
        assertThat(mapper.readValue("\"900\"", AomMetricPeriod.class))
                .isEqualTo(AomMetricPeriod.SEC_900);
    }

    @Test
    @DisplayName("unknown or null period is strictly rejected")
    void unknownValueRejected() {
        assertThatThrownBy(() -> AomMetricPeriod.fromValue("42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("42");
        assertThatThrownBy(() -> AomMetricPeriod.fromSeconds(1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AomMetricPeriod.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
