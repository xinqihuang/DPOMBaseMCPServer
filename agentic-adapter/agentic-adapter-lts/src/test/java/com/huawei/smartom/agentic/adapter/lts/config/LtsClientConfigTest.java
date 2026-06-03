/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.lts.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.huaweicloud.sdk.core.ClientBuilder;
import com.huaweicloud.sdk.lts.v2.LtsClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * LTS 客户端配置类的单元测试。
 *
 * @author h00884391
 * @since 2026-06-03
 */
@ExtendWith(MockitoExtension.class)
class LtsClientConfigTest {

    private final LtsClientConfig config = new LtsClientConfig();

    @Test
    @DisplayName("LtsClient bean is created from HuaweiCloudProperties (ak/sk/projectId/ltsRegion)")
    @SuppressWarnings("unchecked")
    void ltsClientBeanCreated() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setAk("test-ak");
        properties.setSk("test-sk");
        properties.setProjectId("test-project-id");
        properties.setLtsRegion("cn-north-9");

        LtsClient mockClient = mock(LtsClient.class);
        ClientBuilder<LtsClient> mockBuilder = mock(ClientBuilder.class);

        try (MockedStatic<LtsClient> staticMock = mockStatic(LtsClient.class)) {
            staticMock.when(LtsClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.withCredential(any())).thenReturn(mockBuilder);
            when(mockBuilder.withHttpConfig(any())).thenReturn(mockBuilder);
            when(mockBuilder.withRegion(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockClient);

            LtsClient result = config.ltsClient(properties);

            assertThat(result).isNotNull();
        }
    }
}
