/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.huaweicloud.sdk.ces.v1.CesClient;
import com.huaweicloud.sdk.ces.v1.region.CesRegion;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;

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
 * <p>此处在底层 SDK 传输层设置 HTTP 超时，与按调用维度生效的应用层 Resilience4j 重试／限流相互独立。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class CesClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CesClientConfig.class);

    /** SDK transport timeout in seconds. */
    private static final int HTTP_TIMEOUT_SECONDS = 10;

    /**
     * 基于配置的 AK/SK、region 以及 SDK HTTP 超时，构建单例 {@link CesClient}。
     *
     * @param properties 已解析的华为云凭证与 region 配置
     * @return 指向配置 region 的可直接使用的 {@link CesClient}
     */
    @Bean
    public CesClient cesClient(HuaweiCloudProperties properties) {
        BasicCredentials credentials = new BasicCredentials()
                .withAk(properties.getAk())
                .withSk(properties.getSk());

        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig()
                .withTimeout(HTTP_TIMEOUT_SECONDS);

        CesClient client = CesClient.newBuilder()
                .withCredential(credentials)
                .withHttpConfig(httpConfig)
                .withRegion(CesRegion.valueOf(properties.getRegion()))
                .build();

        LOG.info("CesClient initialized, region={}", properties.getRegion());
        return client;
    }
}
