/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AomLogCategory} 受控枚举的单元测试（T26）。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomLogCategoryTest {

    @Test
    @DisplayName("enum contains exactly app_log/node_log/custom_log")
    void containsExactlyThreeValues() {
        assertThat(AomLogCategory.values())
                .extracting(AomLogCategory::getValue)
                .containsExactlyInAnyOrder("app_log", "node_log", "custom_log");
    }

    @Test
    @DisplayName("fromValue accepts both the API literal and the constant name")
    void fromValueAcceptsBothForms() {
        assertThat(AomLogCategory.fromValue("app_log")).isEqualTo(AomLogCategory.APP_LOG);
        assertThat(AomLogCategory.fromValue("APP_LOG")).isEqualTo(AomLogCategory.APP_LOG);
        assertThat(AomLogCategory.fromValue("custom_log")).isEqualTo(AomLogCategory.CUSTOM_LOG);
    }

    @Test
    @DisplayName("unknown or null category is strictly rejected")
    void unknownValueRejected() {
        assertThatThrownBy(() -> AomLogCategory.fromValue("sys_log"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sys_log");
        assertThatThrownBy(() -> AomLogCategory.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
