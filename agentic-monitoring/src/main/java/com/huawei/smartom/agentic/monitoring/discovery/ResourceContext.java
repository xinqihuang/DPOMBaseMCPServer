/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

import java.util.List;

/**
 * 规范化资源发现结果：标识列表 + 缺失能力 + 候选。
 *
 * @param identifiers         已解析标识（含 provenance/ambiguity）
 * @param missingCapabilities 无法完成的映射缺口（按请求目标动态生成）
 * @param candidates          无法唯一映射时的候选
 *
 * @author h00884391
 * @since 2026-08-16
 */
public record ResourceContext(List<ResourceIdentifier> identifiers,
        List<MissingCapability> missingCapabilities, List<ResourceCandidate> candidates) {
}
