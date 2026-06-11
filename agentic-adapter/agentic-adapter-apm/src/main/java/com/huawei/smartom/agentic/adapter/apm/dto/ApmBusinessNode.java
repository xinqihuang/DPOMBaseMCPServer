/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * 单个 APM 应用（business）节点，对应 SDK {@code BusinessNodeModel} 的无损投影（§4.1，9 字段全量）。
 *
 * <p>注意：上游同时返回 {@code default} 与 {@code is_default} 两个独立布尔字段，全部保留；
 * {@code default} 是 Java 关键字，DTO 组件名取 {@code defaultFlag}，JSON 键不变。
 *
 * @param defaultFlag   上游 {@code default} 字段
 * @param displayName   应用展示名称
 * @param epsId         企业项目 ID
 * @param gmtCreate     创建时间（SDK 类型为 {@link LocalDate}）
 * @param gmtModify     修改时间
 * @param id            应用 ID，即后续 APM 工具的 {@code business_id}
 * @param innerDomainId 内部租户 ID
 * @param isDefault     是否默认应用
 * @param name          应用英文名称
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmBusinessNode(
        @JsonProperty("default") Boolean defaultFlag,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("eps_id") String epsId,
        @JsonProperty("gmt_create") LocalDate gmtCreate,
        @JsonProperty("gmt_modify") LocalDate gmtModify,
        @JsonProperty("id") Long id,
        @JsonProperty("inner_domain_id") Integer innerDomainId,
        @JsonProperty("is_default") Boolean isDefault,
        @JsonProperty("name") String name) {
}
