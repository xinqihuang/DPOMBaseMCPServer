/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.transport;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpClient;
import com.huaweicloud.sdk.core.http.HttpMethod;
import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SdkApmAlarmRuleAdminTransport} 认证传输测试。
 *
 * @author h00884391
 * @since 2026-08-26
 */
class SdkApmAlarmRuleAdminTransportTest {

    @Test
    @DisplayName("使用 SDK Core AK/SK 链签名后再发送请求")
    void signsRequestBeforeSending() {
        HttpClient client = mock(HttpClient.class);
        HttpResponse expected = mock(HttpResponse.class);
        when(client.syncInvokeHttp(any(HttpRequest.class))).thenReturn(expected);
        BasicCredentials credentials = new BasicCredentials()
                .withAk("unit-test-ak")
                .withSk("unit-test-sk");
        SdkApmAlarmRuleAdminTransport transport =
                new SdkApmAlarmRuleAdminTransport(credentials, client);
        HttpRequest request = HttpRequest.newBuilder()
                .withEndpoint("https://apm.cn-north-4.myhuaweicloud.com")
                .withPath("/v2/alarm-center/rule/update-rule-disable")
                .withMethod(HttpMethod.PUT)
                .withContentType("application/json")
                .addQueryParam("alarm_rule_id", List.of("17680"))
                .addQueryParam("enable", List.of("false"))
                .build();

        HttpResponse actual = transport.execute(request);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).syncInvokeHttp(captor.capture());
        HttpRequest signed = captor.getValue();
        assertThat(actual).isSameAs(expected);
        assertThat(signed.getHeader("Authorization"))
                .startsWith("SDK-HMAC-SHA256 Access=unit-test-ak")
                .doesNotContain("unit-test-sk");
        assertThat(signed.getHeader("X-Sdk-Date")).isNotBlank();
        assertThat(signed.getQueryParams()).isEqualTo(request.getQueryParams());
    }
}
