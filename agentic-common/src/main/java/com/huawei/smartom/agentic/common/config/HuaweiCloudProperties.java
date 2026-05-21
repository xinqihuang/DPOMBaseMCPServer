package com.huawei.smartom.agentic.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Huawei Cloud connection configuration.
 *
 * <p>AK / SK are injected from environment variables {@code HUAWEICLOUD_AK} / {@code HUAWEICLOUD_SK}
 * via {@code application.yml} placeholders, populated by Vault at the pod level.
 * {@code projectId} is injected from {@code HUAWEICLOUD_PROJECT_ID} and is required by AOM.
 * The {@code region} is bound directly from configuration (e.g. {@code cn-southwest-2}).
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

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAk() {
        return ak;
    }

    public void setAk(String ak) {
        this.ak = ak;
    }

    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}
