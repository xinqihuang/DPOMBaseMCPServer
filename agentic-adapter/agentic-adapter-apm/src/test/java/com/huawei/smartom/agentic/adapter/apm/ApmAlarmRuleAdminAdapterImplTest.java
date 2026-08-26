/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmRuleStatusResponse;
import com.huawei.smartom.agentic.adapter.apm.transport.ApmAlarmRuleAdminTransport;
import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.common.sdk.SdkExceptionMapper;

import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.http.HttpMethod;
import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApmAlarmRuleAdminAdapterImpl} 单元测试。
 *
 * @author h00884391
 * @since 2026-08-26
 */
class ApmAlarmRuleAdminAdapterImplTest {

    private ApmAlarmRuleAdminTransport transport;
    private ApmAlarmRuleAdminAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        transport = mock(ApmAlarmRuleAdminTransport.class);
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setApmRegion("cn-north-4");
        adapter = new ApmAlarmRuleAdminAdapterImpl(
                transport, properties, new SdkExceptionMapper());
    }

    @Test
    @DisplayName("关闭整条规则时构造 PUT 请求并返回确认结果")
    void disablesWholeRule() {
        when(transport.execute(any(HttpRequest.class)))
                .thenReturn(response(200, "{\"ok\":\"ok\"}", "req-disable"));

        ApmAlarmRuleStatusResponse result = adapter.updateAlarmRuleStatus(
                new ApmAlarmRuleStatusRequest(17680L, false));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).execute(captor.capture());
        HttpRequest request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.PUT);
        assertThat(request.getPath()).isEqualTo("/v2/alarm-center/rule/update-rule-disable");
        assertThat(request.getEndpoint()).isEqualTo("https://apm2.cn-north-4.myhuaweicloud.com");
        assertThat(request.getQueryParams()).containsEntry("alarm_rule_id", java.util.List.of("17680"));
        assertThat(request.getQueryParams()).containsEntry("enable", java.util.List.of("false"));
        assertThat(result).isEqualTo(new ApmAlarmRuleStatusResponse(17680L, false, true, "ok"));
    }

    @Test
    @DisplayName("启用整条规则时透传 enable=true")
    void enablesWholeRule() {
        when(transport.execute(any(HttpRequest.class)))
                .thenReturn(response(200, "{\"ok\":\"ok\"}", null));

        adapter.updateAlarmRuleStatus(new ApmAlarmRuleStatusRequest(17680L, true));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).execute(captor.capture());
        assertThat(captor.getValue().getQueryParams())
                .containsEntry("enable", java.util.List.of("true"));
    }

    @Test
    @DisplayName("null、零和负数规则 ID 均在本地拒绝")
    void rejectsInvalidRuleIdsWithoutUpstreamCall() {
        assertInvalid(new ApmAlarmRuleStatusRequest(null, false));
        assertInvalid(new ApmAlarmRuleStatusRequest(0L, false));
        assertInvalid(new ApmAlarmRuleStatusRequest(-1L, false));
        verify(transport, never()).execute(any());
    }

    @Test
    @DisplayName("缺少目标状态时在本地拒绝")
    void rejectsNullEnableWithoutUpstreamCall() {
        assertInvalid(new ApmAlarmRuleStatusRequest(17680L, null));
        verify(transport, never()).execute(any());
    }

    @Test
    @DisplayName("缺少请求时在本地拒绝")
    void rejectsNullRequestWithoutUpstreamCall() {
        assertThatThrownBy(() -> adapter.updateAlarmRuleStatus(null))
                .isInstanceOf(SmartomException.class)
                .matches(this::isInvalidParam);
        verify(transport, never()).execute(any());
    }

    @Test
    @DisplayName("200 响应缺少 ok 成功标记时 fail closed")
    void rejectsInvalidSuccessBody() {
        when(transport.execute(any(HttpRequest.class)))
                .thenReturn(response(200, "{\"ok\":\"unexpected\"}", "req-invalid"));

        assertThatThrownBy(() -> updateDefaultRule())
                .isInstanceOf(SmartomException.class)
                .matches(exception -> hasError(exception, ErrorCode.UPSTREAM_ERROR))
                .matches(exception -> hasTrace(exception, "req-invalid"));
    }

    @Test
    @DisplayName("200 响应 JSON 不可解析时 fail closed 且不暴露响应体")
    void rejectsMalformedSuccessBodyWithoutEchoingIt() {
        String sensitiveBody = "not-json-secret-value";
        when(transport.execute(any(HttpRequest.class)))
                .thenReturn(response(200, sensitiveBody, "req-json"));

        assertThatThrownBy(() -> updateDefaultRule())
                .isInstanceOf(SmartomException.class)
                .hasMessageNotContaining(sensitiveBody)
                .matches(exception -> hasError(exception, ErrorCode.UPSTREAM_ERROR));
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @CsvSource({
            "400, INVALID_PARAM",
            "401, UPSTREAM_AUTH_FAILED",
            "403, UPSTREAM_AUTH_FAILED",
            "429, UPSTREAM_THROTTLED",
            "500, UPSTREAM_ERROR",
            "503, UPSTREAM_ERROR"
    })
    @DisplayName("HTTP 失败映射到统一错误码并保留 request id")
    void mapsHttpFailures(int status, ErrorCode expected) {
        when(transport.execute(any(HttpRequest.class)))
                .thenReturn(response(status, "ignored-sensitive-body", "req-http"));

        assertThatThrownBy(() -> updateDefaultRule())
                .isInstanceOf(SmartomException.class)
                .hasMessageNotContaining("ignored-sensitive-body")
                .matches(exception -> hasError(exception, expected))
                .matches(exception -> hasTrace(exception, "req-http"));
    }

    @Test
    @DisplayName("SDK 读取超时映射为 TIMEOUT")
    void mapsRequestTimeout() {
        when(transport.execute(any(HttpRequest.class)))
                .thenThrow(new RequestTimeoutException("read timed out"));

        assertThatThrownBy(() -> updateDefaultRule())
                .isInstanceOf(SmartomException.class)
                .matches(exception -> hasError(exception, ErrorCode.TIMEOUT));
    }

    @Test
    @DisplayName("SDK 连接失败映射为 TIMEOUT")
    void mapsConnectionFailure() {
        when(transport.execute(any(HttpRequest.class)))
                .thenThrow(new ConnectionException("connect timed out"));

        assertThatThrownBy(() -> updateDefaultRule())
                .isInstanceOf(SmartomException.class)
                .matches(exception -> hasError(exception, ErrorCode.TIMEOUT));
    }

    private void updateDefaultRule() {
        adapter.updateAlarmRuleStatus(new ApmAlarmRuleStatusRequest(17680L, false));
    }

    private void assertInvalid(ApmAlarmRuleStatusRequest request) {
        assertThatThrownBy(() -> adapter.updateAlarmRuleStatus(request))
                .isInstanceOf(SmartomException.class)
                .matches(this::isInvalidParam);
    }

    private boolean isInvalidParam(Throwable throwable) {
        return hasError(throwable, ErrorCode.INVALID_PARAM);
    }

    private boolean hasError(Throwable throwable, ErrorCode expected) {
        return ((SmartomException) throwable).getErrorCode() == expected;
    }

    private boolean hasTrace(Throwable throwable, String expected) {
        return expected.equals(((SmartomException) throwable).getUpstreamTraceId());
    }

    private HttpResponse response(int status, String body, String requestId) {
        return new FakeHttpResponse(status, body, requestId);
    }

    private record FakeHttpResponse(int status, String body, String requestId) implements HttpResponse {

        @Override
        public int getStatusCode() {
            return status;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public long getContentLength() {
            return getBodyAsBytes().length;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            if (requestId == null) {
                return Map.of();
            }
            return Map.of("X-Request-Id", List.of(requestId));
        }

        @Override
        public String getBodyAsString() {
            return body;
        }

        @Override
        public byte[] getBodyAsBytes() {
            return body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(getBodyAsBytes());
        }

        @Override
        public String getHeader(String name) {
            if (requestId != null && "X-Request-Id".equalsIgnoreCase(name)) {
                return requestId;
            }
            return null;
        }
    }
}
