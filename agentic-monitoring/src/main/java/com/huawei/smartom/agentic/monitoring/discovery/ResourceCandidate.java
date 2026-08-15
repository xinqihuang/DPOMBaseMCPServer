/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

/**
 * 无法唯一映射时的候选标识。
 *
 * @param field      字段名，如 {@code apm_app_name}
 * @param value      候选值
 * @param matchType  匹配类型（EXACT_MATCH / NAME_MATCH / UNSCORED），不做无证据的数值置信度
 * @param sourceTool 候选来源工具
 * @param sourceApi  真实 adapter/华为云 API 标识
 * @param nextStep   消除歧义所需的下一步标识；不得要求用户重复提供已有字段
 *
 * @author h00884391
 * @since 2026-08-16
 */
public record ResourceCandidate(String field, String value, MatchType matchType, String sourceTool,
        String sourceApi, String nextStep) {
}
