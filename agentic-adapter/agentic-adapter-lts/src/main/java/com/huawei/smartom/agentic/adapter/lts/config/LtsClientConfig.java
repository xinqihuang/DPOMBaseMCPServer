/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.sdk.HuaweiCloudClientFactory;

import com.huaweicloud.sdk.lts.v2.LtsClient;
import com.huaweicloud.sdk.lts.v2.region.LtsRegion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 {@link HuaweiCloudProperties} 构建 {@link LtsClient} Bean。
 *
 * <p>LTS 是项目级服务（请求 URI 含 {@code {project_id}} 路径变量），由 SDK 从
 * {@link com.huaweicloud.sdk.core.auth.BasicCredentials#withProjectId 凭据} 自动注入。
 * 因此凭据必须用 {@link HuaweiCloudClientFactory#credentialsWithProjectId} 而非仅 AK/SK 的版本。
 *
 * <p>LTS 在部分 region 不开服，使用独立配置项 {@code huaweicloud.lts-region} 解耦。
 *
 * @author h00884391
 * @since 2026-06-03
 */
@Configuration
public class LtsClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LtsClientConfig.class);

    /**
     * 创建 LTS SDK 客户端 Bean。
     *
     * @param properties 华为云连接配置；ak / sk / projectId / ltsRegion 不能为空
     * @return 已配置完成的 {@link LtsClient}
     */
    @Bean
    public LtsClient ltsClient(HuaweiCloudProperties properties) {
        LtsClient client = LtsClient.newBuilder()
                .withCredential(HuaweiCloudClientFactory.credentialsWithProjectId(properties))
                .withHttpConfig(HuaweiCloudClientFactory.defaultHttpConfig())
                .withRegion(LtsRegion.valueOf(properties.getLtsRegion()))
                .build();

        LOG.info("LtsClient initialized, ltsRegion={}, projectId={}",
                properties.getLtsRegion(), properties.getProjectId());
        return client;
    }
}
