/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.sdk;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;

/**
 * 各华为云 SDK 客户端共用的构造样板。提供
 * 凭据（含 / 不含 {@code projectId}）以及默认 HTTP 配置（含传输层超时）的工厂方法，
 * 避免 CES / AOM / APM 等 client config 各自重复同一段 builder 模板。
 *
 * <p>具体的 {@code newBuilder().withRegion(...).build()} 调用因 SDK 类型不同
 * 仍由各 adapter 的 {@code @Configuration} 自行完成。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public final class HuaweiCloudClientFactory {

    /** SDK 传输层默认超时（秒）。各 client 如有特殊需求可在自己的 {@code @Configuration} 中覆盖。 */
    public static final int DEFAULT_HTTP_TIMEOUT_SECONDS = 10;

    private HuaweiCloudClientFactory() {
        // 工具类，禁止实例化
    }

    /**
     * 用 AK/SK 构造基础凭据。适用于 CES v1 / v2、APM 等不需要 projectId 的 SDK。
     *
     * @param properties 已绑定的华为云连接配置，ak / sk 不能为空
     * @return 已配置 AK/SK 的 {@link BasicCredentials}
     */
    public static BasicCredentials credentials(HuaweiCloudProperties properties) {
        return new BasicCredentials()
                .withAk(properties.getAk())
                .withSk(properties.getSk());
    }

    /**
     * 在 AK/SK 之上追加 {@code projectId}。适用于 AOM 等需要按租户区分的 SDK。
     *
     * @param properties 已绑定的华为云连接配置，ak / sk / projectId 不能为空
     * @return 已配置 AK/SK 与 projectId 的 {@link BasicCredentials}
     */
    public static BasicCredentials credentialsWithProjectId(HuaweiCloudProperties properties) {
        return credentials(properties).withProjectId(properties.getProjectId());
    }

    /**
     * 返回带默认传输层超时的 SDK {@link HttpConfig}。
     *
     * @return 默认 {@link HttpConfig}
     */
    public static HttpConfig defaultHttpConfig() {
        return HttpConfig.getDefaultHttpConfig()
                .withTimeout(DEFAULT_HTTP_TIMEOUT_SECONDS);
    }
}
