/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 华为云连接配置。
 *
 * <p>AK / SK 通过 {@code application.yml} 中的占位符从环境变量 {@code HUAWEICLOUD_AK} / {@code HUAWEICLOUD_SK}
 * 注入，由 Vault 在 Pod 层面写入。{@code projectId} 从 {@code HUAWEICLOUD_PROJECT_ID} 注入，AOM 调用必须依赖该值。
 * {@code region} 直接绑定自配置项（例如 {@code cn-southwest-2}）。
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
     * 返回已配置的华为云 region id。
     *
     * @return region id，Spring 完成绑定后不会为 {@code null}
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置华为云 region id。
     *
     * @param region region id，例如 {@code cn-southwest-2}
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * 返回华为云 Access Key。
     *
     * @return access key，Spring 完成绑定后不会为 {@code null}
     */
    public String getAk() {
        return ak;
    }

    /**
     * 设置华为云 Access Key。
     *
     * @param ak access key，从环境变量 {@code HUAWEICLOUD_AK} 注入
     */
    public void setAk(String ak) {
        this.ak = ak;
    }

    /**
     * 返回华为云 Secret Key。
     *
     * @return secret key，Spring 完成绑定后不会为 {@code null}
     */
    public String getSk() {
        return sk;
    }

    /**
     * 设置华为云 Secret Key。
     *
     * @param sk secret key，从环境变量 {@code HUAWEICLOUD_SK} 注入
     */
    public void setSk(String sk) {
        this.sk = sk;
    }

    /**
     * 返回华为云 project id。
     *
     * @return project id，AOM 接口必须依赖该值
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * 设置华为云 project id。
     *
     * @param projectId project id，从环境变量 {@code HUAWEICLOUD_PROJECT_ID} 注入
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}
