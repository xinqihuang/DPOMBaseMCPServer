/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

/**
 * 资源标识的取值性质：精确值或由其它锚点推导的值。
 *
 * @author h00884391
 * @since 2026-08-16
 */
public enum IdentifierKind {
    /** 精确值：直接来自请求锚点或上游真实返回。 */
    EXACT,
    /** 推导值：由其它标识/锚点间接推导，需标注来源与歧义。 */
    DERIVED
}
