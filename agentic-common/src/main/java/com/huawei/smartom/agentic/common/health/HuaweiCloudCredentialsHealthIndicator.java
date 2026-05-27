/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.health;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 当必需的华为云凭据未配置时返回 DOWN，从而阻断 Pod 的 readiness 探针。
 *
 * <p>Bean 名为 {@code huaweiCloudCredentials}，通过 {@code application.yml} 中的
 * {@code management.endpoint.health.group.readiness.include} 加入 readiness 组。
 *
 * <p>这里会一并检查 projectId，原因是 AOM 接口必须依赖该值。CES 不需要 projectId，即使缺失也能继续工作；
 * 但整体 readiness 探针在所有必需凭据齐全之前都会报告 DOWN。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component("huaweiCloudCredentials")
public class HuaweiCloudCredentialsHealthIndicator implements HealthIndicator {

    private final HuaweiCloudProperties properties;

    /**
     * 基于给定的配置属性构造一个新的 {@code HuaweiCloudCredentialsHealthIndicator}。
     *
     * @param properties 已解析的华为云凭据 / region 配置属性，将被本组件用于健康检查
     */
    public HuaweiCloudCredentialsHealthIndicator(HuaweiCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (isBlank(properties.getAk())) {
            return Health.down().withDetail("reason", "HUAWEICLOUD_AK is missing").build();
        }
        if (isBlank(properties.getSk())) {
            return Health.down().withDetail("reason", "HUAWEICLOUD_SK is missing").build();
        }
        if (isBlank(properties.getRegion())) {
            return Health.down().withDetail("reason", "huaweicloud.region is missing").build();
        }
        if (isBlank(properties.getProjectId())) {
            return Health.down().withDetail("reason", "HUAWEICLOUD_PROJECT_ID is missing").build();
        }
        return Health.up().withDetail("region", properties.getRegion()).build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
