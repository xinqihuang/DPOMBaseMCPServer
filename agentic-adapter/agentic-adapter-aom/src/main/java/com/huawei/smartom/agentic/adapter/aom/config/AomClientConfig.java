/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.sdk.HuaweiCloudClientFactory;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.region.AomRegion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 {@link HuaweiCloudProperties} 构建 {@link AomClient} Bean。
 *
 * <p>AOM 除 AK/SK 之外还需要 {@code projectId}。凭据从环境变量 {@code HUAWEICLOUD_AK}、
 * {@code HUAWEICLOUD_SK} 与 {@code HUAWEICLOUD_PROJECT_ID} 读取，由 Vault 在 Pod 层面注入。
 *
 * <p>Region 通过 {@link AomRegion#valueOf(String)} 解析。凭据与 HTTP 超时由
 * {@link HuaweiCloudClientFactory} 统一构造，重试与限流由 Resilience4j 负责。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class AomClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AomClientConfig.class);

    /**
     * 创建 AOM SDK 客户端 Bean。
     *
     * @param properties 华为云连接配置；ak、sk、projectId 与 region 均不能为空
     *                   （由健康检查在启动时校验）
     * @return 已配置完成的 {@link AomClient}
     */
    @Bean
    public AomClient aomClient(HuaweiCloudProperties properties) {
        AomClient client = AomClient.newBuilder()
                .withCredential(HuaweiCloudClientFactory.credentialsWithProjectId(properties))
                .withHttpConfig(HuaweiCloudClientFactory.defaultHttpConfig())
                .withRegion(AomRegion.valueOf(properties.getRegion()))
                .build();

        LOG.info("AomClient initialized, region={}, projectId={}",
                properties.getRegion(), properties.getProjectId());
        return client;
    }
}
