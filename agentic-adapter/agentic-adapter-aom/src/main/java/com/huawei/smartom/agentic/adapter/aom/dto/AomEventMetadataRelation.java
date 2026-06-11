/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import java.util.List;

/**
 * {@code list_aom_events} 工具中描述单个 metadata 过滤条件的 DTO，对应华为云 AOM
 * {@code ListEvents} 请求体 {@code metadata_relation} 数组的一个元素（SDK {@code RelationModel}）。
 *
 * @param key      事件 metadata 的键，必填
 * @param value    匹配的取值列表，可为 {@code null}
 * @param relation 组合关系，取值 {@code AND} / {@code OR} / {@code NOT}（SDK RelationEnum 封闭集，
 *                 service 层校验）
 * @author h00884391
 * @since 2026-06-11
 */
public record AomEventMetadataRelation(
        String key,
        List<String> value,
        String relation) {
}
