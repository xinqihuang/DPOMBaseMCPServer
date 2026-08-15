/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.obs.services.ObsClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于服务端配置构建 {@link ObsClient} Bean，仅当 {@code dpom.obs.enabled=true} 时装配。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Configuration
@EnableConfigurationProperties(ObsProperties.class)
public class ObsClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ObsClientConfig.class);

    /**
     * 构建指向服务端配置 endpoint 的 OBS 客户端。
     *
     * @param huaweiProperties 华为云 AK/SK 配置
     * @param obsProperties    OBS 服务端配置（endpoint 等）
     * @return 可直接使用的 {@link ObsClient}
     */
    @Bean
    @ConditionalOnProperty(name = "dpom.obs.enabled", havingValue = "true")
    public ObsClient obsClient(HuaweiCloudProperties huaweiProperties, ObsProperties obsProperties) {
        ObsClient client = new ObsClient(
                huaweiProperties.getAk(), huaweiProperties.getSk(), obsProperties.getEndpoint());
        LOG.info("ObsClient initialized, endpoint={}", obsProperties.getEndpoint());
        return client;
    }
}
