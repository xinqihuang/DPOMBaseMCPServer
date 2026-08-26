/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.sdk.HuaweiCloudClientFactory;
import com.huawei.smartom.agentic.adapter.apm.transport.ApmAlarmRuleAdminTransport;
import com.huawei.smartom.agentic.adapter.apm.transport.SdkApmAlarmRuleAdminTransport;

import com.huaweicloud.sdk.apm.v1.ApmClient;
import com.huaweicloud.sdk.apm.v1.region.ApmRegion;
import com.huaweicloud.sdk.core.impl.DefaultHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 {@link HuaweiCloudProperties} 构建 {@link ApmClient} Bean。
 *
 * <p>APM service 仅在部分 region 提供（如 {@code cn-north-4}、{@code ru-moscow-1}），
 * 因此使用独立配置项 {@code huaweicloud.apm-region} 解耦。凭据与 HTTP 超时由
 * {@link HuaweiCloudClientFactory} 统一构造。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Configuration
public class ApmClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ApmClientConfig.class);

    /**
     * 创建 APM SDK 客户端 Bean。
     *
     * @param properties 华为云连接配置；ak / sk / apmRegion 不能为空
     * @return 已配置完成的 {@link ApmClient}
     */
    @Bean
    public ApmClient apmClient(HuaweiCloudProperties properties) {
        ApmClient client = ApmClient.newBuilder()
                .withCredential(HuaweiCloudClientFactory.credentials(properties))
                .withHttpConfig(HuaweiCloudClientFactory.defaultHttpConfig())
                .withRegion(ApmRegion.valueOf(properties.getApmRegion()))
                .build();

        LOG.info("ApmClient initialized, apmRegion={}", properties.getApmRegion());
        return client;
    }

    /**
     * 创建未生成 APM 规则管理接口使用的认证 HTTP 传输。
     *
     * @param properties 华为云连接配置；ak / sk 不能为空
     * @return 使用统一凭据和超时配置的规则管理传输
     */
    @Bean
    public ApmAlarmRuleAdminTransport apmAlarmRuleAdminTransport(HuaweiCloudProperties properties) {
        return new SdkApmAlarmRuleAdminTransport(
                HuaweiCloudClientFactory.credentials(properties),
                new DefaultHttpClient(HuaweiCloudClientFactory.defaultHttpConfig()));
    }
}
