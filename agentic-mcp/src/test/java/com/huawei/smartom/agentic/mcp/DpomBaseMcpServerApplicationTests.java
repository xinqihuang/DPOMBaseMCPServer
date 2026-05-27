/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp;

import com.huaweicloud.sdk.aom.v2.AomClient;
import com.huaweicloud.sdk.ces.v1.CesClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * 启动完整 Spring 应用上下文，验证 MCP starter、工具、CES adapter、AOM adapter 及通用 Bean 等
 * 所有依赖装配正确。
 *
 * <p>注入测试用凭据，使健康检查与 SDK 客户端 Bean 能正常构造，避免实际调用华为云 IAM 端点。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@SpringBootTest
@TestPropertySource(properties = {
    "huaweicloud.region=cn-southwest-2",
    "huaweicloud.ak=test-ak",
    "huaweicloud.sk=test-sk",
    "huaweicloud.project-id=test-project-id"
})
class DpomBaseMcpServerApplicationTests {

    @MockBean
    private CesClient cesClient;

    @MockBean
    private AomClient aomClient;

    @Test
    @DisplayName("Spring application context loads")
    void contextLoads() {
        // The mere fact that the context started successfully validates wiring.
    }
}
