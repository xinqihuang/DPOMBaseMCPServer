/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
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
 * Builds the {@link CesClient} bean from {@link HuaweiCloudProperties}.
 *
 * <p>Region is resolved via {@link CesRegion#valueOf(String)} from the configured region id
 * (e.g. {@code "cn-southwest-2"} for Guiyang One).
 *
 * <p>HTTP timeout is set on the underlying SDK transport here, in addition to the
 * application-level Resilience4j retry / rate-limiter applied per call.
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
     * Builds the singleton {@link CesClient} from the configured AK/SK, region and SDK HTTP timeout.
     *
     * @param properties resolved Huawei Cloud credential and region properties
     * @return a ready-to-use {@link CesClient} pointing at the configured region
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
