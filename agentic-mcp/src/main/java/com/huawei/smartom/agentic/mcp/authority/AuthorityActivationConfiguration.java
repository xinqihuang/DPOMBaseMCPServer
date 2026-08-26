/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.authority;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 默认关闭且在启动阶段失败关闭的权威激活门禁。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthorityActivationProperties.class)
@ConditionalOnProperty(prefix = "dpom.investigation.authority", name = "enabled", havingValue = "true")
public class AuthorityActivationConfiguration {
    /**
     * 创建启动门禁。
     *
     * @param properties 部署事实
     * @return 启动校验器
     */
    @Bean
    public ApplicationRunner authorityActivationGuard(AuthorityActivationProperties properties) {
        return arguments -> properties.validateActivation();
    }
}
