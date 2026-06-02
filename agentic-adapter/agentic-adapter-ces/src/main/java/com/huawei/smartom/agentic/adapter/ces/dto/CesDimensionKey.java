/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 华为云 CES 已知监控维度键的目录枚举。
 *
 * <p>维度键标识资源类型（如 ECS 的 {@code instance_id}、EVS 的 {@code disk_name}），
 * 不同命名空间使用不同维度键。本枚举仅枚举常用键，调用方仍以 {@code String} 承载维度名称，
 * 允许未列入的键被使用。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public enum CesDimensionKey {

    /** ECS 实例 ID。 */
    INSTANCE_ID("instance_id"),

    /** EVS 磁盘名称。 */
    DISK_NAME("disk_name"),

    /** VPC 带宽 ID。 */
    BANDWIDTH_ID("bandwidth_id"),

    /** ELB 监听器 ID。 */
    LBAAS_LISTENER_ID("lbaas_listener_id"),

    /** ELB 后端实例 ID。 */
    LBAAS_INSTANCE_ID("lbaas_instance_id"),

    /** RDS 实例 ID。 */
    RDS_CLUSTER_ID("rds_cluster_id"),

    /** OBS 桶名。 */
    BUCKET_NAME("bucket_name");

    private final String value;

    CesDimensionKey(String value) {
        this.value = value;
    }

    /**
     * 返回维度键的字符串字面量。
     *
     * @return 维度键，如 {@code instance_id}
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 按字面量查找枚举，未匹配返回 {@code null}（容忍未知维度键）。
     *
     * @param value 维度键字面量
     * @return 匹配的枚举常量；未匹配或 {@code value} 为 {@code null} 时返回 {@code null}
     */
    @JsonCreator
    public static CesDimensionKey fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CesDimensionKey key : values()) {
            if (key.value.equals(value)) {
                return key;
            }
        }
        return null;
    }
}
