/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.transport;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpClient;
import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;

import java.util.Objects;

/**
 * 使用华为云 SDK Core 凭据链和 HTTP 客户端的规则管理传输实现。
 *
 * <p>认证请求仅在内存中存在，本实现不输出请求头或凭据信息。
 *
 * @author h00884391
 * @since 2026-08-26
 */
public class SdkApmAlarmRuleAdminTransport implements ApmAlarmRuleAdminTransport {

    private final BasicCredentials credentials;
    private final HttpClient httpClient;

    /**
     * 构造 SDK Core 传输实现。
     *
     * @param credentials 华为云 AK/SK 凭据
     * @param httpClient  使用统一超时配置的 SDK HTTP 客户端
     */
    public SdkApmAlarmRuleAdminTransport(BasicCredentials credentials, HttpClient httpClient) {
        this.credentials = Objects.requireNonNull(credentials, "credentials must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest authenticated = credentials.syncProcessAuthRequest(request, httpClient);
        return httpClient.syncInvokeHttp(authenticated);
    }
}
