/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.common.health;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN if required Huawei Cloud credentials are not configured, blocking pod readiness.
 *
 * <p>Bean name resolves to {@code huaweiCloudCredentials}, included via
 * {@code management.endpoint.health.group.readiness.include} in {@code application.yml}.
 *
 * <p>projectId is checked here because AOM APIs require it. CES does not need projectId and will
 * continue to work even when projectId is missing; however, the overall readiness probe will report
 * DOWN until all required credentials are present.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component("huaweiCloudCredentials")
public class HuaweiCloudCredentialsHealthIndicator implements HealthIndicator {

    private final HuaweiCloudProperties properties;

    /**
     * Constructs a new {@code HuaweiCloudCredentialsHealthIndicator} backed by the given properties.
     *
     * @param properties the resolved Huawei Cloud credential/region properties to be checked
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
