/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 华为云 CES 常用监控指标的目录枚举。
 *
 * <p>每个枚举常量记录指标的 {@code id}（API 字面量）、所属 {@link CesNamespace}、主维度
 * {@link CesDimensionKey}、单位与中文描述。本枚举不强制——DTO 中的 {@code metricName}
 * 仍以 {@code String} 承载，允许未列入的指标被使用。
 *
 * <p>当前覆盖 ECS 基础监控指标，取值见
 * <a href="https://support.huaweicloud.com/intl/en-us/usermanual-ecs/ecs_03_1002.html">
 * Basic ECS Metrics</a>。后续新增服务时按需扩展。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public enum CesMetric {

    // ---- SYS.ECS 基础监控 ----

    /** CPU 使用率。 */
    ECS_CPU_UTIL("cpu_util", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, "%", "CPU 使用率"),

    /** 内存使用率（需安装 Agent）。 */
    ECS_MEM_UTIL("mem_util", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, "%", "内存使用率"),

    /** 磁盘使用率（需安装 Agent）。 */
    ECS_DISK_UTIL_INBAND("disk_util_inband", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, "%", "磁盘使用率"),

    /** 磁盘读带宽。 */
    ECS_DISK_READ_BYTES_RATE(
            "disk_read_bytes_rate", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, "byte/s", "磁盘读带宽"),

    /** 磁盘写带宽。 */
    ECS_DISK_WRITE_BYTES_RATE(
            "disk_write_bytes_rate", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, "byte/s", "磁盘写带宽"),

    /** 磁盘读 IOPS。 */
    ECS_DISK_READ_REQUESTS_RATE(
            "disk_read_requests_rate", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "Request/s", "磁盘读 IOPS"),

    /** 磁盘写 IOPS。 */
    ECS_DISK_WRITE_REQUESTS_RATE(
            "disk_write_requests_rate", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "Request/s", "磁盘写 IOPS"),

    /** 带内入网速率（虚拟机内部视角）。 */
    ECS_NETWORK_INCOMING_BYTES_RATE_INBAND(
            "network_incoming_bytes_rate_inband", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "byte/s", "带内入网速率"),

    /** 带内出网速率（虚拟机内部视角）。 */
    ECS_NETWORK_OUTGOING_BYTES_RATE_INBAND(
            "network_outgoing_bytes_rate_inband", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "byte/s", "带内出网速率"),

    /** 带外入网速率（Hypervisor 视角）。 */
    ECS_NETWORK_INCOMING_BYTES_AGGREGATE_RATE(
            "network_incoming_bytes_aggregate_rate", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "byte/s", "带外入网速率"),

    /** 带外出网速率（Hypervisor 视角）。 */
    ECS_NETWORK_OUTGOING_BYTES_AGGREGATE_RATE(
            "network_outgoing_bytes_aggregate_rate", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "byte/s", "带外出网速率"),

    /** T6 突发实例 CPU 积分消耗。 */
    ECS_CPU_CREDIT_USAGE(
            "cpu_credit_usage", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, null, "T6 实例 CPU 积分消耗"),

    /** T6 突发实例 CPU 积分余额。 */
    ECS_CPU_CREDIT_BALANCE(
            "cpu_credit_balance", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, null, "T6 实例 CPU 积分余额"),

    /** 网络连接数（TCP + UDP）。 */
    ECS_NETWORK_VM_CONNECTIONS(
            "network_vm_connections", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID, "Count", "网络连接数"),

    /** 入方向带宽（公网 + 内网）。 */
    ECS_NETWORK_VM_BANDWIDTH_IN(
            "network_vm_bandwidth_in", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "Byte/s", "入方向带宽"),

    /** 出方向带宽（公网 + 内网）。 */
    ECS_NETWORK_VM_BANDWIDTH_OUT(
            "network_vm_bandwidth_out", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "Byte/s", "出方向带宽"),

    /** 入方向 PPS。 */
    ECS_NETWORK_VM_PPS_IN(
            "network_vm_pps_in", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "Packet/s", "入方向 PPS"),

    /** 出方向 PPS。 */
    ECS_NETWORK_VM_PPS_OUT(
            "network_vm_pps_out", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "Packet/s", "出方向 PPS"),

    /** 新建连接数（TCP/UDP/ICMP）。 */
    ECS_NETWORK_VM_NEWCONNECTIONS(
            "network_vm_newconnections", CesNamespace.SYS_ECS, CesDimensionKey.INSTANCE_ID,
            "connect/s", "新建连接数");

    private final String id;
    private final CesNamespace namespace;
    private final CesDimensionKey primaryDimension;
    private final String unit;
    private final String description;

    CesMetric(String id, CesNamespace namespace, CesDimensionKey primaryDimension,
              String unit, String description) {
        this.id = id;
        this.namespace = namespace;
        this.primaryDimension = primaryDimension;
        this.unit = unit;
        this.description = description;
    }

    /**
     * 返回 CES API 使用的指标 id。
     *
     * @return 指标 id，如 {@code cpu_util}
     */
    @JsonValue
    public String getId() {
        return id;
    }

    /**
     * 返回指标所属命名空间。
     *
     * @return 命名空间
     */
    public CesNamespace getNamespace() {
        return namespace;
    }

    /**
     * 返回指标的主维度键。
     *
     * @return 维度键
     */
    public CesDimensionKey getPrimaryDimension() {
        return primaryDimension;
    }

    /**
     * 返回指标单位（部分指标无单位，返回 {@code null}）。
     *
     * @return 单位字面量或 {@code null}
     */
    public String getUnit() {
        return unit;
    }

    /**
     * 返回中文描述。
     *
     * @return 描述文本
     */
    public String getDescription() {
        return description;
    }

    /**
     * 在指定命名空间下按 id 查找指标，未匹配返回 {@code null}（容忍未列入指标）。
     *
     * @param namespace 命名空间，可为 {@code null}（仅按 id 匹配）
     * @param id        指标 id
     * @return 匹配的枚举常量；未匹配或 {@code id} 为 {@code null} 时返回 {@code null}
     */
    public static CesMetric find(CesNamespace namespace, String id) {
        if (id == null) {
            return null;
        }
        for (CesMetric metric : values()) {
            if (!metric.id.equals(id)) {
                continue;
            }
            if (namespace == null || namespace == metric.namespace) {
                return metric;
            }
        }
        return null;
    }

    /**
     * 按 id 查找指标，未匹配返回 {@code null}。
     *
     * @param id 指标 id
     * @return 匹配的枚举常量；未匹配或 {@code id} 为 {@code null} 时返回 {@code null}
     */
    @JsonCreator
    public static CesMetric fromId(String id) {
        return find(null, id);
    }
}
