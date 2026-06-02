/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.sdk.HuaweiCloudClientFactory;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.region.CesRegion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 {@link HuaweiCloudProperties} 构建 {@link CesClient} Bean。
 *
 * <p>region 通过 {@link CesRegion#valueOf(String)} 从配置的 region id 解析得到
 * （例如贵阳一区为 {@code "cn-southwest-2"}）。
 *
 * <p>凭据与 HTTP 超时由 {@link HuaweiCloudClientFactory} 统一构造，与按调用维度生效的应用层
 * Resilience4j 重试／限流相互独立。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class CesClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CesClientConfig.class);

    /**
     * 基于配置的 AK/SK、region 以及 SDK HTTP 超时，构建单例 {@link CesClient}。
     *
     * @param properties 已解析的华为云凭证与 region 配置
     * @return 指向配置 region 的可直接使用的 {@link CesClient}
     */
    @Bean
    public CesClient cesClient(HuaweiCloudProperties properties) {
        CesClient client = CesClient.newBuilder()
                .withCredential(HuaweiCloudClientFactory.credentials(properties))
                .withHttpConfig(HuaweiCloudClientFactory.defaultHttpConfig())
                .withRegion(CesRegion.valueOf(properties.getRegion()))
                .build();

        LOG.info("CesClient initialized, region={}", properties.getRegion());
        return client;
    }
}
