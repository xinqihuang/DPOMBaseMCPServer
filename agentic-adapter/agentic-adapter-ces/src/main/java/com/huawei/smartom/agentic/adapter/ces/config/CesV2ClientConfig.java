/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.sdk.HuaweiCloudClientFactory;

import com.huaweicloud.sdk.ces.v2.CesClient;
import com.huaweicloud.sdk.ces.v2.region.CesRegion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 {@link HuaweiCloudProperties} 构建华为云 CES V2 SDK 客户端 Bean。
 *
 * <p>V2 客户端在 v1 之上提供新一代接口，包括告警屏蔽规则（{@code BatchUpdateNotificationMasks} /
 * {@code BatchDeleteNotificationMasks} / {@code ListNotificationMasks}）。与 V1 客户端独立共存。
 *
 * <p>凭据与 HTTP 超时由 {@link HuaweiCloudClientFactory} 统一构造。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Configuration
public class CesV2ClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CesV2ClientConfig.class);

    /**
     * 创建 CES V2 SDK 客户端 Bean。
     *
     * @param properties 华为云连接配置；ak / sk / region 不能为空
     * @return 已配置完成的 V2 {@link CesClient}
     */
    @Bean
    public CesClient cesV2Client(HuaweiCloudProperties properties) {
        CesClient client = CesClient.newBuilder()
                .withCredential(HuaweiCloudClientFactory.credentials(properties))
                .withHttpConfig(HuaweiCloudClientFactory.defaultHttpConfig())
                .withRegion(CesRegion.valueOf(properties.getRegion()))
                .build();

        LOG.info("CesV2Client initialized, region={}", properties.getRegion());
        return client;
    }
}
