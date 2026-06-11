/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AomFillValue} 受控枚举的单元测试（T26）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomFillValueTest {

    @Test
    @DisplayName("enum contains exactly -1/0/null/average; 'null' is the literal string")
    void containsExactlyFourValues() {
        assertThat(AomFillValue.values())
                .extracting(AomFillValue::getValue)
                .containsExactlyInAnyOrder("-1", "0", "null", "average");
        assertThat(AomFillValue.NULL_FILL.getValue()).isEqualTo("null");
    }

    @Test
    @DisplayName("fromValue accepts both the API literal and the constant name")
    void fromValueAcceptsBothForms() {
        assertThat(AomFillValue.fromValue("null")).isEqualTo(AomFillValue.NULL_FILL);
        assertThat(AomFillValue.fromValue("NULL_FILL")).isEqualTo(AomFillValue.NULL_FILL);
        assertThat(AomFillValue.fromValue("-1")).isEqualTo(AomFillValue.MINUS_ONE);
    }

    @Test
    @DisplayName("unknown or null fill_value is strictly rejected")
    void unknownValueRejected() {
        assertThatThrownBy(() -> AomFillValue.fromValue("nan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nan");
        assertThatThrownBy(() -> AomFillValue.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
