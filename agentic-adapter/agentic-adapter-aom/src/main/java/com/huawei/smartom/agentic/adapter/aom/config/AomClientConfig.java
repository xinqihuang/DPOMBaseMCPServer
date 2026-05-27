/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.adapter.aom.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.aom.v2.region.AomRegion;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link AomClient} bean from {@link HuaweiCloudProperties}.
 *
 * <p>AOM requires a {@code projectId} in addition to AK/SK. The credentials are read from
 * environment variables {@code HUAWEICLOUD_AK}, {@code HUAWEICLOUD_SK}, and
 * {@code HUAWEICLOUD_PROJECT_ID}, injected via Vault at the pod level.
 *
 * <p>Region is resolved via {@link AomRegion#valueOf(String)}.
 * HTTP timeout is set on the SDK transport layer; Resilience4j handles retry/rate-limiting.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Configuration
public class AomClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AomClientConfig.class);

    /** SDK transport timeout in seconds. */
    private static final int HTTP_TIMEOUT_SECONDS = 10;

    /**
     * Creates the AOM SDK client bean.
     *
     * @param properties Huawei Cloud connection properties; ak, sk, projectId and region must
     *                   be non-blank (validated at startup by the health indicator)
     * @return a configured {@link AomClient}
     */
    @Bean
    public AomClient aomClient(HuaweiCloudProperties properties) {
        BasicCredentials credentials = new BasicCredentials()
                .withProjectId(properties.getProjectId())
                .withAk(properties.getAk())
                .withSk(properties.getSk());

        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig()
                .withTimeout(HTTP_TIMEOUT_SECONDS);

        AomClient client = AomClient.newBuilder()
                .withCredential(credentials)
                .withHttpConfig(httpConfig)
                .withRegion(AomRegion.valueOf(properties.getRegion()))
                .build();

        LOG.info("AomClient initialized, region={}, projectId={}", properties.getRegion(), properties.getProjectId());
        return client;
    }
}
