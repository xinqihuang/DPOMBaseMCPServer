/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AomMetricStatistic} 受控枚举的单元测试（T26）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomMetricStatisticTest {

    @Test
    @DisplayName("enum contains exactly the five API-supported statistics, sampleCount camelCase")
    void containsExactlyFiveValues() {
        assertThat(AomMetricStatistic.values())
                .extracting(AomMetricStatistic::getValue)
                .containsExactlyInAnyOrder("maximum", "minimum", "sum", "average", "sampleCount");
    }

    @Test
    @DisplayName("fromValue accepts both the API literal and the constant name")
    void fromValueAcceptsBothForms() {
        assertThat(AomMetricStatistic.fromValue("sampleCount"))
                .isEqualTo(AomMetricStatistic.SAMPLE_COUNT);
        assertThat(AomMetricStatistic.fromValue("SAMPLE_COUNT"))
                .isEqualTo(AomMetricStatistic.SAMPLE_COUNT);
        assertThat(AomMetricStatistic.fromValue("average")).isEqualTo(AomMetricStatistic.AVERAGE);
    }

    @Test
    @DisplayName("unknown or null statistic is strictly rejected")
    void unknownValueRejected() {
        assertThatThrownBy(() -> AomMetricStatistic.fromValue("p99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p99");
        assertThatThrownBy(() -> AomMetricStatistic.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
