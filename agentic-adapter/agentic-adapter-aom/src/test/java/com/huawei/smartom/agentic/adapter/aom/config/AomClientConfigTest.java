/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.core.ClientBuilder;

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
 * Aom Client Config Test.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@ExtendWith(MockitoExtension.class)
class AomClientConfigTest {

    private final AomClientConfig config = new AomClientConfig();

    @Test
    @DisplayName("AomClient bean is created from HuaweiCloudProperties")
    @SuppressWarnings("unchecked")
    void aomClientBeanCreated() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setAk("test-ak");
        properties.setSk("test-sk");
        properties.setRegion("cn-southwest-2");
        properties.setProjectId("test-project-id");

        AomClient mockClient = mock(AomClient.class);
        ClientBuilder<AomClient> mockBuilder = mock(ClientBuilder.class);

        try (MockedStatic<AomClient> staticMock = mockStatic(AomClient.class)) {
            staticMock.when(AomClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.withCredential(any())).thenReturn(mockBuilder);
            when(mockBuilder.withHttpConfig(any())).thenReturn(mockBuilder);
            when(mockBuilder.withRegion(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockClient);

            AomClient result = config.aomClient(properties);

            assertThat(result).isNotNull();
        }
    }
}
