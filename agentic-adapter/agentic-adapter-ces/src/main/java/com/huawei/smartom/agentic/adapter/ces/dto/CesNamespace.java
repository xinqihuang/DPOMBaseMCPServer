/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 华为云 CES 受支持命名空间的受控枚举（T24，CLAUDE.md §4.3 (a)）。
 *
 * <p>本枚举是 CES 查询工具请求侧的<strong>封闭集</strong>：工具入参直接以本枚举承载，
 * 枚举常量会被 Spring AI 反射进工具 JSON Schema，使 Agent 只能从合法取值中选择，
 * 不再凭先验拼 namespace 字符串。响应侧 DTO（如 {@link CesMetricInfo}）仍以
 * {@code String} 承载（§4.1 无损投影），两侧职责不同，请勿混用。
 *
 * <p>已知边界：RDS for MySQL 集群版的 {@code SYS.RDS_MYSQL_CLUSTER} <strong>不进本枚举</strong>——
 * Agent 无法判定实例部署形态，统一传 {@link #SYS_RDS}，由 service 层探测后透明路由
 * （见 monitoring 模块的 RDS fallback 实现与 {@code docs/tasks/T24-ces-namespace-enum.md}）。
 *
 * <p>取值见 <a href="https://support.huaweicloud.com/intl/en-us/usermanual-ces/ces_01_0059.html">
 * Services Interconnected with Cloud Eye</a>。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public enum CesNamespace {

    /** 弹性云服务器。 */
    SYS_ECS("SYS.ECS", "弹性云服务器"),

    /** 对象存储服务。 */
    SYS_OBS("SYS.OBS", "对象存储服务"),

    /** 云硬盘。 */
    SYS_EVS("SYS.EVS", "云硬盘"),

    /** 虚拟私有云 / 区域级 EIP。 */
    SYS_VPC("SYS.VPC", "虚拟私有云/区域级EIP"),

    /** 全域弹性公网 IP。 */
    SYS_GEIP("SYS.GEIP", "全域弹性公网IP"),

    /** 分布式消息服务。 */
    SYS_DMS("SYS.DMS", "分布式消息服务"),

    /** 分布式缓存服务。 */
    SYS_DCS("SYS.DCS", "分布式缓存服务"),

    /** Web 应用防火墙。 */
    SYS_WAF("SYS.WAF", "Web应用防火墙"),

    /** 云防火墙。 */
    SYS_CFW("SYS.CFW", "云防火墙"),

    /** API 网关（共享版）。 */
    SYS_APIG("SYS.APIG", "API网关(共享版)"),

    /** 关系型数据库（含 MySQL 集群版，由 service 层 fallback 透明路由）。 */
    SYS_RDS("SYS.RDS", "关系型数据库"),

    /** 弹性负载均衡。 */
    SYS_ELB("SYS.ELB", "弹性负载均衡"),

    /** 云解析服务。 */
    SYS_DNS("SYS.DNS", "云解析服务"),

    /** NAT 网关。 */
    SYS_NAT("SYS.NAT", "NAT网关");

    private final String value;
    private final String description;

    CesNamespace(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 返回 CES API 使用的命名空间字面量。
     *
     * @return 字面量，如 {@code SYS.ECS}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 返回该命名空间对应服务的中文说明。
     *
     * @return 中文说明，不会为 {@code null} 或空串
     */
    public String getDescription() {
        return description;
    }

    /**
     * 将入参字符串解析为枚举。兼容两种写法：API 字面量（{@code SYS.ECS}）
     * 与枚举常量名（{@code SYS_ECS}）——JSON Schema 对枚举的渲染形式可能取后者，
     * 两者都接受可避免 Schema 渲染形式与反序列化规则不一致。
     *
     * @param value 命名空间字面量或常量名
     * @return 对应的枚举常量
     * @throws IllegalArgumentException {@code value} 为 {@code null} 或不在受支持集合时抛出
     */
    @JsonCreator
    public static CesNamespace fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("namespace must not be null");
        }
        for (CesNamespace ns : values()) {
            if (ns.value.equals(value) || ns.name().equals(value)) {
                return ns;
            }
        }
        throw new IllegalArgumentException(
                "unsupported CES namespace: " + value + ", expected one of "
                        + Arrays.toString(values()));
    }
}
