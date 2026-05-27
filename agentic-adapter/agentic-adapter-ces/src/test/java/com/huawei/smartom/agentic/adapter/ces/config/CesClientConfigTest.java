/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.adapter.ces.config;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import com.huaweicloud.sdk.ces.v1.CesClient;
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
 * Ces Client Config Test.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@ExtendWith(MockitoExtension.class)
class CesClientConfigTest {

    private final CesClientConfig config = new CesClientConfig();

    @Test
    @DisplayName("CesClient bean is created from HuaweiCloudProperties")
    @SuppressWarnings("unchecked")
    void cesClientBeanCreated() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setAk("test-ak");
        properties.setSk("test-sk");
        properties.setRegion("cn-southwest-2");

        CesClient mockClient = mock(CesClient.class);
        ClientBuilder<CesClient> mockBuilder = mock(ClientBuilder.class);

        try (MockedStatic<CesClient> staticMock = mockStatic(CesClient.class)) {
            staticMock.when(CesClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.withCredential(any())).thenReturn(mockBuilder);
            when(mockBuilder.withHttpConfig(any())).thenReturn(mockBuilder);
            when(mockBuilder.withRegion(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockClient);

            CesClient result = config.cesClient(properties);

            assertThat(result).isNotNull();
        }
    }
}
