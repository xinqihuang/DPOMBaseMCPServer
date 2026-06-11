/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AomAlertType} 受控枚举的单元测试（T27）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomAlertTypeTest {

    @Test
    @DisplayName("enum contains exactly the two SDK-supported alert types")
    void containsExactlyTwoValues() {
        assertThat(AomAlertType.values()).hasSize(2);
        assertThat(AomAlertType.ACTIVE_ALERT.getValue()).isEqualTo("active_alert");
        assertThat(AomAlertType.HISTORY_ALERT.getValue()).isEqualTo("history_alert");
    }

    @Test
    @DisplayName("fromValue accepts both the API literal and the constant name")
    void fromValueAcceptsBothForms() {
        assertThat(AomAlertType.fromValue("active_alert")).isEqualTo(AomAlertType.ACTIVE_ALERT);
        assertThat(AomAlertType.fromValue("ACTIVE_ALERT")).isEqualTo(AomAlertType.ACTIVE_ALERT);
        assertThat(AomAlertType.fromValue("history_alert")).isEqualTo(AomAlertType.HISTORY_ALERT);
    }

    @Test
    @DisplayName("unknown or null alert type is strictly rejected")
    void unknownValueRejected() {
        assertThatThrownBy(() -> AomAlertType.fromValue("all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all");
        assertThatThrownBy(() -> AomAlertType.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
