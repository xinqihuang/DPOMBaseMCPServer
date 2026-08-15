/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 审批控制面配置装配：注册 {@code dpom.approval.*} 配置属性。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Configuration
@EnableConfigurationProperties(ApprovalProperties.class)
public class ApprovalConfig {
}
