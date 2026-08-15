/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 证据转移审批控制面的服务端配置（{@code dpom.approval.*}）。
 *
 * <p>HMAC 密钥仅来自服务端配置（可轮换），不回显、不进日志；默认关闭。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@ConfigurationProperties(prefix = "dpom.approval")
public class ApprovalProperties {

    private boolean enabled = false;
    private String hmacSecret = "";
    private String hmacPreviousSecret = "";
    private int timestampToleranceSeconds = 300;
    private int approvalTtlSeconds = 3600;
    private String storeFile = "data/dpom-approvals.json";
    private String nonceStoreFile = "data/dpom-approval-nonces.json";
    private int maxBodyBytes = 4096;
    private int nonceCacheSize = 10000;

    /**
     * 返回审批控制面是否启用。
     *
     * @return true 表示注册控制面 REST 端点
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置审批控制面是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 HMAC 签名密钥。
     *
     * @return HMAC 密钥，为空表示 fail-closed
     */
    public String getHmacSecret() {
        return hmacSecret;
    }

    /**
     * 设置 HMAC 签名密钥。
     *
     * @param hmacSecret HMAC 密钥
     */
    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * 返回上一代 HMAC 密钥（用于平滑轮换，可为空）。
     *
     * @return 上一代 HMAC 密钥，为空表示未配置
     */
    public String getHmacPreviousSecret() {
        return hmacPreviousSecret;
    }

    /**
     * 设置上一代 HMAC 密钥（轮换时保留旧密钥以平滑过渡）。
     *
     * @param hmacPreviousSecret 上一代 HMAC 密钥
     */
    public void setHmacPreviousSecret(String hmacPreviousSecret) {
        this.hmacPreviousSecret = hmacPreviousSecret;
    }

    /**
     * 返回 timestamp 容忍窗口（秒）。
     *
     * @return 容忍窗口秒数
     */
    public int getTimestampToleranceSeconds() {
        return timestampToleranceSeconds;
    }

    /**
     * 设置 timestamp 容忍窗口（秒）。
     *
     * @param timestampToleranceSeconds 容忍窗口秒数
     */
    public void setTimestampToleranceSeconds(int timestampToleranceSeconds) {
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    /**
     * 返回审批有效期（秒）。
     *
     * @return 审批有效期秒数
     */
    public int getApprovalTtlSeconds() {
        return approvalTtlSeconds;
    }

    /**
     * 设置审批有效期（秒）。
     *
     * @param approvalTtlSeconds 审批有效期秒数
     */
    public void setApprovalTtlSeconds(int approvalTtlSeconds) {
        this.approvalTtlSeconds = approvalTtlSeconds;
    }

    /**
     * 返回审批持久化文件路径。
     *
     * @return 审批存储文件路径
     */
    public String getStoreFile() {
        return storeFile;
    }

    /**
     * 设置审批持久化文件路径。
     *
     * @param storeFile 审批存储文件路径
     */
    public void setStoreFile(String storeFile) {
        this.storeFile = storeFile;
    }

    /**
     * 返回 nonce 防重放持久化文件路径。
     *
     * @return nonce 持久化文件路径
     */
    public String getNonceStoreFile() {
        return nonceStoreFile;
    }

    /**
     * 设置 nonce 防重放持久化文件路径。
     *
     * @param nonceStoreFile nonce 持久化文件路径
     */
    public void setNonceStoreFile(String nonceStoreFile) {
        this.nonceStoreFile = nonceStoreFile;
    }

    /**
     * 返回控制面请求体最大字节数。
     *
     * @return 请求体最大字节数
     */
    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    /**
     * 设置控制面请求体最大字节数。
     *
     * @param maxBodyBytes 请求体最大字节数
     */
    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    /**
     * 返回 nonce 防重放缓存容量。
     *
     * @return nonce 缓存容量
     */
    public int getNonceCacheSize() {
        return nonceCacheSize;
    }

    /**
     * 设置 nonce 防重放缓存容量。
     *
     * @param nonceCacheSize nonce 缓存容量
     */
    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }
}
