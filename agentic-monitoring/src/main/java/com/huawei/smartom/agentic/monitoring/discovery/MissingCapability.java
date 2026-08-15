/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

/**
 * 当前能力无法完成的映射缺口。
 *
 * @param target        目标标识，如 {@code ecs_instance_id}
 * @param reason        无法完成的原因
 * @param requiredInput 消除该缺口所需的额外输入
 *
 * @author h00884391
 * @since 2026-08-16
 */
public record MissingCapability(String target, String reason, String requiredInput) {
}
