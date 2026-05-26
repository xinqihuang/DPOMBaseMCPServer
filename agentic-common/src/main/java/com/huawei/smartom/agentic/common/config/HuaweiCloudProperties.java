/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
 */

package com.huawei.smartom.agentic.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Huawei Cloud connection configuration.
 *
 * <p>AK / SK are injected from environment variables {@code HUAWEICLOUD_AK} / {@code HUAWEICLOUD_SK}
 * via {@code application.yml} placeholders, populated by Vault at the pod level.
 * {@code projectId} is injected from {@code HUAWEICLOUD_PROJECT_ID} and is required by AOM.
 * The {@code region} is bound directly from configuration (e.g. {@code cn-southwest-2}).
 *
 * @author h00884391
 * @since 2026-05-21
 */
@ConfigurationProperties(prefix = "huaweicloud")
public class HuaweiCloudProperties {

    /** Huawei Cloud region id, e.g. "cn-southwest-2" (Guiyang One). */
    private String region;

    /** Access Key, injected from env HUAWEICLOUD_AK. */
    private String ak;

    /** Secret Key, injected from env HUAWEICLOUD_SK. */
    private String sk;

    /** Project ID, injected from env HUAWEICLOUD_PROJECT_ID. Required by AOM APIs. */
    private String projectId;

    /**
     * Returns the configured Huawei Cloud region id.
     *
     * @return the region id, never {@code null} once Spring binding has run
     */
    public String getRegion() {
        return region;
    }

    /**
     * Sets the Huawei Cloud region id.
     *
     * @param region the region id, e.g. {@code cn-southwest-2}
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * Returns the Huawei Cloud Access Key.
     *
     * @return the access key, never {@code null} once Spring binding has run
     */
    public String getAk() {
        return ak;
    }

    /**
     * Sets the Huawei Cloud Access Key.
     *
     * @param ak the access key, injected from env {@code HUAWEICLOUD_AK}
     */
    public void setAk(String ak) {
        this.ak = ak;
    }

    /**
     * Returns the Huawei Cloud Secret Key.
     *
     * @return the secret key, never {@code null} once Spring binding has run
     */
    public String getSk() {
        return sk;
    }

    /**
     * Sets the Huawei Cloud Secret Key.
     *
     * @param sk the secret key, injected from env {@code HUAWEICLOUD_SK}
     */
    public void setSk(String sk) {
        this.sk = sk;
    }

    /**
     * Returns the Huawei Cloud project id.
     *
     * @return the project id, required by AOM APIs
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Sets the Huawei Cloud project id.
     *
     * @param projectId the project id, injected from env {@code HUAWEICLOUD_PROJECT_ID}
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}
