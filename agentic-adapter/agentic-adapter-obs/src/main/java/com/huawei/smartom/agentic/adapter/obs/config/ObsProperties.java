/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OBS 证据转移的服务端配置（{@code dpom.obs.*}）。
 *
 * <p>bucket / prefix / endpoint / kms-key-id / 大小上限均只来自服务端配置，不来自调用方。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@ConfigurationProperties(prefix = "dpom.obs")
public class ObsProperties {

    private boolean enabled = false;
    private boolean transferToolsEnabled = false;
    private boolean automaticStorageEnabled = false;
    private String bucket = "";
    private String prefix = "";
    private String endpoint = "";
    private String kmsKeyId = "";
    private String serviceCode = "";
    private int maxBytes = 1048576;
    private int maxEntries = 200;

    /**
     * 返回 OBS 证据转移是否启用。
     *
     * @return true 表示启用真实 OBS 适配器，false 表示默认 fail-closed
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 OBS 证据转移是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 OBS 转移工具是否注册（独立于 write-tools 的 gate）。
     *
     * @return true 表示注册 OBS 转移 MCP 工具
     */
    public boolean isTransferToolsEnabled() {
        return transferToolsEnabled;
    }

    /**
     * 设置 OBS 转移工具是否注册。
     *
     * @param transferToolsEnabled 是否注册 OBS 转移工具
     */
    public void setTransferToolsEnabled(boolean transferToolsEnabled) {
        this.transferToolsEnabled = transferToolsEnabled;
    }

    /**
     * 返回自动诊断证据存储是否启用。
     *
     * @return true 表示自动写入 OBS
     */
    public boolean isAutomaticStorageEnabled() {
        return automaticStorageEnabled;
    }

    /**
     * 设置自动诊断证据存储是否启用。
     *
     * @param automaticStorageEnabled 是否自动写入 OBS
     */
    public void setAutomaticStorageEnabled(boolean automaticStorageEnabled) {
        this.automaticStorageEnabled = automaticStorageEnabled;
    }

    /**
     * 返回 OBS bucket 名称。
     *
     * @return bucket 名称
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 设置 OBS bucket 名称。
     *
     * @param bucket bucket 名称
     */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /**
     * 返回对象键前缀。
     *
     * @return 对象键前缀
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 设置对象键前缀。
     *
     * @param prefix 对象键前缀
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 返回 OBS endpoint。
     *
     * @return OBS endpoint，例如 {@code https://obs.cn-north-9.myhuaweicloud.com}
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置 OBS endpoint。
     *
     * @param endpoint OBS endpoint
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 返回 SSE-KMS 服务端加密的 KMS 密钥 ID。
     *
     * @return KMS 密钥 ID，为空表示未配置
     */
    public String getKmsKeyId() {
        return kmsKeyId;
    }

    /**
     * 设置 SSE-KMS 服务端加密的 KMS 密钥 ID。
     *
     * @param kmsKeyId KMS 密钥 ID
     */
    public void setKmsKeyId(String kmsKeyId) {
        this.kmsKeyId = kmsKeyId;
    }

    /**
     * 返回写入对象键所使用的服务编码。
     *
     * @return 服务编码
     */
    public String getServiceCode() {
        return serviceCode;
    }

    /**
     * 设置写入对象键所使用的服务编码。
     *
     * @param serviceCode 服务编码
     */
    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    /**
     * 返回证据包最大字节数。
     *
     * @return 最大字节数
     */
    public int getMaxBytes() {
        return maxBytes;
    }

    /**
     * 设置证据包最大字节数。
     *
     * @param maxBytes 最大字节数
     */
    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * 返回证据包最大条目数。
     *
     * @return 最大条目数
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * 设置证据包最大条目数。
     *
     * @param maxEntries 最大条目数
     */
    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

}
