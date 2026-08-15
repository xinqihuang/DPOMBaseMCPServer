/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

/**
 * 候选匹配类型：区分精确匹配、名称匹配与无评分证据。
 *
 * @author h00884391
 * @since 2026-08-16
 */
public enum MatchType {
    /** 精确匹配：上游返回唯一且与锚点一致。 */
    EXACT_MATCH,
    /** 名称匹配：按名称/关键词命中多个候选。 */
    NAME_MATCH,
    /** 无评分证据：无法给出可辩护的置信度，仅列出候选。 */
    UNSCORED
}
