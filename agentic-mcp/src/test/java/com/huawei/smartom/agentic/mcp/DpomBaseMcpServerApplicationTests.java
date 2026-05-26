/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved
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
 * Boots the full application context to ensure all wiring (MCP starter, tools,
 * CES adapter, AOM adapter, common beans) is valid.
 *
 * <p>Test credentials are injected so the health indicator and SDK client beans construct
 * without calling live Huawei Cloud IAM endpoints.
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
