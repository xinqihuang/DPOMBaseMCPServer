/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CesNamespace} 受控枚举的单元测试（T24）。
 *
 * <p>覆盖：14 个受支持取值的封闭性、字面量/中文说明完备性、两种写法的解析兼容、
 * 未知取值严格拒绝、集群版命名空间不进枚举。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class CesNamespaceTest {

    @Test
    @DisplayName("enum contains exactly the 15 supported namespaces")
    void containsExactly15SupportedValues() {
        assertThat(CesNamespace.values()).hasSize(15);
        assertThat(CesNamespace.values())
                .extracting(CesNamespace::getValue)
                .containsExactlyInAnyOrder(
                        "SYS.ECS", "SYS.OBS", "SYS.EVS", "SYS.VPC", "SYS.GEIP",
                        "SYS.DMS", "SYS.DCS", "SYS.WAF", "SYS.CFW", "SYS.APIG",
                        "SYS.RDS", "SYS.RDS_MYSQL_CLUSTER", "SYS.ELB", "SYS.DNS", "SYS.NAT");
    }

    @Test
    @DisplayName("every value carries a SYS.* literal and a non-blank Chinese description")
    void valueAndDescriptionPopulated() {
        for (CesNamespace ns : CesNamespace.values()) {
            assertThat(ns.getValue()).as("value of %s", ns).startsWith("SYS.");
            assertThat(ns.getDescription()).as("description of %s", ns).isNotBlank();
        }
    }

    @Test
    @DisplayName("fromValue accepts both the API literal and the constant name")
    void fromValueAcceptsBothForms() {
        assertThat(CesNamespace.fromValue("SYS.ECS")).isEqualTo(CesNamespace.SYS_ECS);
        assertThat(CesNamespace.fromValue("SYS_ECS")).isEqualTo(CesNamespace.SYS_ECS);
        assertThat(CesNamespace.fromValue("SYS.RDS")).isEqualTo(CesNamespace.SYS_RDS);
    }

    @Test
    @DisplayName("unknown or null namespace is strictly rejected")
    void unknownValueRejected() {
        assertThatThrownBy(() -> CesNamespace.fromValue("SYS.UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYS.UNKNOWN");
        assertThatThrownBy(() -> CesNamespace.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("both RDS deployment-form namespaces are explicitly selectable")
    void bothRdsNamespacesSelectable() {
        assertThat(CesNamespace.fromValue("SYS.RDS")).isEqualTo(CesNamespace.SYS_RDS);
        assertThat(CesNamespace.fromValue("SYS.RDS_MYSQL_CLUSTER"))
                .isEqualTo(CesNamespace.SYS_RDS_MYSQL_CLUSTER);
    }
}
