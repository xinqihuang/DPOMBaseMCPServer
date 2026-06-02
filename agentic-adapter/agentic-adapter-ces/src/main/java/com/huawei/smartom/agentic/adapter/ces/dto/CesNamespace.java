/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 华为云 CES 已知命名空间的目录枚举。
 *
 * <p>命名空间形如 {@code service.item}，对应 CES 各服务接入的资源类型。
 * 由于华为云后续会持续新增服务，本枚举仅作为"常用命名空间"参考，
 * 调用方 DTO 仍以 {@code String} 承载，允许新增命名空间在未更新本枚举的情况下被使用。
 *
 * <p>取值见 <a href="https://support.huaweicloud.com/intl/en-us/usermanual-ces/ces_01_0059.html">
 * Services Interconnected with Cloud Eye</a>。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public enum CesNamespace {

    /** 弹性云服务器（ECS）基础监控。 */
    SYS_ECS("SYS.ECS"),

    /** 弹性云服务器（ECS）操作系统监控扩展，由 Agent 上报。 */
    AGT_ECS("AGT.ECS"),

    /** 云硬盘（EVS）。 */
    SYS_EVS("SYS.EVS"),

    /** 虚拟私有云（VPC）。 */
    SYS_VPC("SYS.VPC"),

    /** 弹性负载均衡（ELB）。 */
    SYS_ELB("SYS.ELB"),

    /** 弹性伸缩（AS）。 */
    SYS_AS("SYS.AS"),

    /** 关系型数据库（RDS）。 */
    SYS_RDS("SYS.RDS"),

    /** 文档数据库（DDS）。 */
    SYS_DDS("SYS.DDS"),

    /** 分布式缓存（DCS）。 */
    SYS_DCS("SYS.DCS"),

    /** 对象存储服务（OBS）。 */
    SYS_OBS("SYS.OBS");

    private final String value;

    CesNamespace(String value) {
        this.value = value;
    }

    /**
     * 返回命名空间的字符串字面量。
     *
     * @return 字面量，如 {@code SYS.ECS}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 按字面量查找枚举，未匹配返回 {@code null}（容忍未知命名空间）。
     *
     * @param value 命名空间字面量
     * @return 匹配的枚举常量；未匹配或 {@code value} 为 {@code null} 时返回 {@code null}
     */
    @JsonCreator
    public static CesNamespace fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CesNamespace ns : values()) {
            if (ns.value.equals(value)) {
                return ns;
            }
        }
        return null;
    }
}
