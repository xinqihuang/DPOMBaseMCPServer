/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 默认关闭的权威调查查询与 SSE 装配。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProgressApiProperties.class)
@ConditionalOnProperty(prefix = "dpom.investigation.progress-api", name = "enabled", havingValue = "true")
public class ProgressApiConfiguration {
    /**
     * 在监听端口前验证安全与容量参数。
     *
     * @param properties 配置
     * @return 启动校验器
     */
    @Bean
    public ApplicationRunner progressApiConfigurationValidator(ProgressApiProperties properties) {
        return arguments -> properties.validate();
    }

    /**
     * 创建只读 Portal 授权校验器。
     *
     * @param properties 配置
     * @return 授权校验器
     */
    @Bean
    public PortalAuthorization portalAuthorization(ProgressApiProperties properties) {
        return new PortalAuthorization(properties);
    }
}
