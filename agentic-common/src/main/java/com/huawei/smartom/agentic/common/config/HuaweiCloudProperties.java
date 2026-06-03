/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
     * APM region id (e.g. {@code cn-north-4}). APM service is currently available in a smaller
     * set of regions than AOM/CES, so this is decoupled from {@link #region}. Defaults to
     * {@code cn-north-4}.
     */
    private String apmRegion = "cn-north-4";

    /**
     * APM business id (the {@code x-business-id} HTTP header required by trace search APIs).
     * Optional — when null the adapter will not set the header.
     */
    private Long apmBusinessId;

    /**
     * LTS region id（例如 {@code cn-north-9}、{@code af-north-1}）。LTS 在部分 region 提供，
     * 与主 region 解耦。默认在 {@code application.yml} 给出 {@code cn-north-9}。
     */
    private String ltsRegion = "cn-north-9";

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

    /**
     * 返回 APM service 使用的 region id（与 {@link #region} 独立配置）。
     *
     * @return APM region id，默认 {@code cn-north-4}
     */
    public String getApmRegion() {
        return apmRegion;
    }

    /**
     * 设置 APM service 使用的 region id。
     *
     * @param apmRegion APM region id，例如 {@code cn-north-4}
     */
    public void setApmRegion(String apmRegion) {
        this.apmRegion = apmRegion;
    }

    /**
     * 返回 APM trace 搜索接口需要的 {@code x-business-id} 头部值。
     *
     * @return APM 应用 id，可能为 {@code null}
     */
    public Long getApmBusinessId() {
        return apmBusinessId;
    }

    /**
     * 设置 APM trace 搜索接口需要的 {@code x-business-id} 头部值。
     *
     * @param apmBusinessId APM 应用 id
     */
    public void setApmBusinessId(Long apmBusinessId) {
        this.apmBusinessId = apmBusinessId;
    }

    /**
     * 返回 LTS service 使用的 region id（与 {@link #region} 独立配置）。
     *
     * @return LTS region id，默认 {@code cn-north-9}
     */
    public String getLtsRegion() {
        return ltsRegion;
    }

    /**
     * 设置 LTS service 使用的 region id。
     *
     * @param ltsRegion LTS region id，例如 {@code cn-north-9}
     */
    public void setLtsRegion(String ltsRegion) {
        this.ltsRegion = ltsRegion;
    }
}
